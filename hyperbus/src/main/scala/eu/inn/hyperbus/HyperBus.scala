package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.impl.MacroApi
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.Try
import scala.util.control.NonFatal

trait HyperBusApi {
  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDeserializer: ResponseDeserializer[RESP]): Future[RESP]

  def publish[REQ <: Request[Body]](request: REQ): Future[PublishResult]

  def process[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
                                                       method: String,
                                                       contentType: Option[String],
                                                       requestDeserializer: RequestDeserializer[REQ])
                                                      (handler: (REQ) => Future[RESP]): String

  def subscribe[REQ <: Request[Body]](topic: Topic,
                                      method: String,
                                      contentType: Option[String],
                                      groupName: String,
                                      requestDeserializer: RequestDeserializer[REQ])
                                     (handler: (REQ) => Future[Unit]): String

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class HyperBus(val transportManager: TransportManager)(implicit val executionContext: ExecutionContext) extends HyperBusApi {
  protected trait Subscription[REQ <: Request[Body]] {
    def requestDeserializer: RequestDeserializer[REQ]
  }
  protected val subscriptions = new Subscriptions[SubKey, Subscription[_]]
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler[_])]
  protected val log = LoggerFactory.getLogger(this.getClass)

  def <~[REQ <: Request[Body]](request: REQ): Future[Response[Body]] = macro HyperBusMacro.ask[REQ]

  def <|[REQ <: Request[Body]](request: REQ): Future[PublishResult] = macro HyperBusMacro.publish[REQ]

  def |>[IN <: Request[Body]](groupName: String)
                                    (handler: (IN) => Future[Unit]): String = macro HyperBusMacro.subscribe[IN]

  def ~>[REQ <: Request[Body]](handler: REQ => Future[Response[Body]]): String = macro HyperBusMacro.process[REQ]

  def <~(request: DynamicRequest): Future[Response[DynamicBody]] = {
    ask(request,
      macroApiImpl.responseDeserializer(_,_,PartialFunction.empty)
    ).asInstanceOf[Future[Response[DynamicBody]]]
  }

  def <|(request: DynamicRequest): Future[PublishResult] = {
    publish(request)
  }

  protected case class RequestReplySubscription[REQ <: Request[Body]](
                                                                       handler: (REQ) => Future[Response[Body]],
                                                                       requestDeserializer: RequestDeserializer[REQ]) extends Subscription[REQ]

  protected case class PubSubSubscription[REQ <: Request[Body]](
                                                                 handler: (REQ) => Future[Unit],
                                                                 requestDeserializer: RequestDeserializer[REQ]) extends Subscription[REQ]

  protected case class SubKey(method: String, contentType: Option[String])

  protected abstract class UnderlyingHandler[REQ <: Request[Body]](routeKey: String) {
    protected def getSubscription(in: REQ): Option[Subscription[REQ]] = getSubscription(in.method, in.body.contentType)

    protected def getSubscription(method: String, contentType: Option[String]): Option[Subscription[REQ]] = {
      subscriptions.get(routeKey).subRoutes.get(SubKey(method, contentType)).orElse {
        // at first try exact match
        subscriptions.get(routeKey).subRoutes.get(SubKey(method, None)) // at second without contentType
      } map { subscriptionList =>
        subscriptionList.getRandomSubscription.asInstanceOf[Subscription[REQ]]
      }
    }

    def deserializer(inputStream: InputStream): REQ = {
      try {
        MessageDeserializer.deserializeRequestWith[REQ](inputStream) { (requestHeader, requestBodyJson) =>
          getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
            subscription.requestDeserializer(requestHeader, requestBodyJson)
          } getOrElse {
            DynamicRequest(requestHeader, requestBodyJson).asInstanceOf[REQ] // todo: why? remove and throw
          }
        }
      }
      catch {
        case re: ErrorResponse ⇒ throw re
        case NonFatal(e) =>
          val error = BadRequest(ErrorBody(DefError.REQUEST_PARSE_ERROR, Some(e.toString)))
          logError("Can't decode request", error)
          throw error
      }
    }
  }

  protected class UnderlyingRequestReplyHandler[REQ <: Request[Body]](routeKey: String)
    extends UnderlyingHandler[REQ](routeKey) {
    def handler(in: REQ): Future[Response[Body]] = {
      getSubscription(in) map {
        case y: RequestReplySubscription[REQ] ⇒
          val futureResult = y.handler(in)
          futureResult.recoverWith {
            case z: Response[_] ⇒ Future.successful(z)
            case t: Throwable ⇒ Future.successful(unhandledException(routeKey, in, t))
          }
      } getOrElse {
        Future.successful(unhandledRequest(routeKey, in))
      }
    }
  }

  protected class UnderlyingPubSubHandler[REQ <: Request[Body]](routeKey: String)
    extends UnderlyingHandler[REQ](routeKey) {
    def handler(in: REQ): Future[Unit] = {
      getSubscription(in).map {
        case s: PubSubSubscription[REQ] ⇒
          s.handler(in)
      } getOrElse {
        Future.successful(unhandledPublication(routeKey, in))
      }
    }
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDeserializer: ResponseDeserializer[RESP]): Future[RESP] = {

    val outputDeserializer = MessageDeserializer.deserializeResponseWith(_: InputStream)(responseDeserializer)
    transportManager.ask[RESP](request, outputDeserializer) map {
      case throwable: Throwable ⇒ throw throwable
      case other ⇒ other
    }
  }

  def publish[REQ <: Request[Body]](request: REQ): Future[PublishResult] = {
    transportManager.publish(request)
  }

  def process[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
                                                            method: String,
                                                            contentType: Option[String],
                                                            requestDeserializer: RequestDeserializer[REQ])
                                                           (handler: (REQ) => Future[RESP]): String = {
    val routeKey = getRouteKey(topic.urlFilter, None)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        RequestReplySubscription(handler.asInstanceOf[(REQ) => Future[Response[Body]]], requestDeserializer)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingRequestReplyHandler[REQ](routeKey)
        val d: Deserializer[REQ] = uh.deserializer
        val uid = transportManager.process[REQ](topic, d, exceptionSerializer)(uh.handler)
        underlyingSubscriptions += routeKey ->(uid, uh)
      }
      r
    }
  }

  def subscribe[REQ <: Request[Body]](topic: Topic,
                                      method: String,
                                      contentType: Option[String],
                                      groupName: String,
                                      requestDeserializer: RequestDeserializer[REQ])
                                     (handler: (REQ) => Future[Unit]): String = {
    val routeKey = getRouteKey(topic.urlFilter, Some(groupName))

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        PubSubSubscription(handler, requestDeserializer)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingPubSubHandler[REQ](routeKey)
        val d: Deserializer[REQ] = uh.deserializer
        val uid = transportManager.subscribe[REQ](topic, groupName, d)(uh.handler)
        underlyingSubscriptions += routeKey ->(uid, uh)
      }
      r
    }
  }

  def off(subscriptionId: String): Unit = {
    underlyingSubscriptions.synchronized {
      subscriptions.getRouteKeyById(subscriptionId) foreach { routeKey =>
        val cnt = subscriptions.get(routeKey).subRoutes.foldLeft(0){ (c, x) =>
          c + x._2.size
        }
        if (cnt <= 1) {
          underlyingSubscriptions.get(routeKey).foreach(k => transportManager.off(k._1))
        }
      }
      subscriptions.remove(subscriptionId)
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    transportManager.shutdown(duration)
  }

  protected def exceptionSerializer(exception: Throwable, outputStream: java.io.OutputStream): Unit = {
    exception match {
      case r: ErrorResponse ⇒ r.serialize(outputStream)
      case t ⇒
        val error = InternalServerError(ErrorBody(DefError.INTERNAL_ERROR, Some(t.getMessage)))
        logError("Unhandled exception", error)
        error.serialize(outputStream)
    }
  }

  val macroApiImpl = new MacroApi {
    def responseDeserializer(responseHeader: ResponseHeader,
                        responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                        bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body] = {
      val body =
        if (bodyDeserializer.isDefinedAt(responseHeader))
          bodyDeserializer(responseHeader)(responseHeader.contentType, responseBodyJson)
        else
          StandardResponseBody(responseHeader, responseBodyJson)
      StandardResponse(responseHeader, body)
    }
  }

  protected def logError(msg: String, error: HyperBusException[ErrorBody]): Unit = {
    log.error(msg + ". #" + error.body.errorId, error)
  }

  protected def safeErrorMessage(msg: String, request: Request[Body], routeKey: String): String = {
    msg + " " + safe(() => request.method) + routeKey +
      safe(() => request.body.contentType.map("@" + _).getOrElse(""))
  }

  protected def unhandledRequest(routeKey: String, request: Request[Body]): Response[Body] = {
    val s = safeErrorMessage("Unhandled request", request, routeKey)
    InternalServerError(ErrorBody(DefError.HANDLER_NOT_FOUND, Some(s)))
  }

  protected def unhandledPublication(routeKey: String, request: Request[Body]): Unit = {
    log.error(safeErrorMessage("Unhandled publication", request, routeKey))
  }

  protected def unhandledException(routeKey: String, request: Request[Body], exception: Throwable): Response[Body] = {
    val errorBody = ErrorBody(DefError.INTERNAL_ERROR, Some(
      safeErrorMessage(s"Unhandled exception: ${exception.getMessage}", request, routeKey)
    ))
    log.error(errorBody.message + ". #" + errorBody.errorId, exception)
    InternalServerError(errorBody)
  }

  protected def responseSerializerNotFound(response: Response[Body]) = log.error("Can't serialize response: {}", response)

  protected def getRouteKey(urlFilter: Filter, groupName: Option[String]) = {
    val url = urlFilter.asInstanceOf[SpecificValue].value // todo: implement other filters?

    groupName.map {
      url + "#" + _
    } getOrElse url
  }

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
