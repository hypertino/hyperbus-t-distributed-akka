package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.impl.MacroApi
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.{TransportRequestMatcher, TextMatcher}
import eu.inn.hyperbus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.Try
import scala.util.control.NonFatal

class HyperBus(val transportManager: TransportManager,
               val defaultGroupName: Option[String] = None)(implicit val executionContext: ExecutionContext)
  extends HyperBusApi {

  protected trait Subscription[REQ <: Request[Body]] {
    def requestDeserializer: RequestDeserializer[REQ]
  }

  protected val subscriptions = new Subscriptions[SubKey, Subscription[_]]
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler[_])]
  protected val log = LoggerFactory.getLogger(this.getClass)

  def onEvent(requestMatcher: TransportRequestMatcher, groupName: Option[String])
             (handler: (DynamicRequest) => Future[Unit]): String = {
    onEvent[DynamicRequest](requestMatcher, groupName, DynamicRequest.apply)(handler)
  }

  def onCommand(requestMatcher: TransportRequestMatcher)
               (handler: DynamicRequest => Future[_ <: Response[Body]]): String = {
    onCommand[Response[Body], DynamicRequest](requestMatcher, DynamicRequest.apply)(handler)
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
          logError("Can't deserialize request", error, e)
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

  def onCommand[RESP <: Response[Body], REQ <: Request[Body]](requestMatcher: TransportRequestMatcher,
                                                              requestDeserializer: RequestDeserializer[REQ])
                                                             (handler: (REQ) => Future[RESP]): String = {
    val routeKey = getRouteKey(requestMatcher, None)
    val method = requestMatcher.headers.get(Header.METHOD).map(_.specific).getOrElse(
      throw new IllegalArgumentException(s"method is not set on matcher headers: $requestMatcher")
    )
    val contentType = requestMatcher.headers.get(Header.CONTENT_TYPE).map(_.specific)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        RequestReplySubscription(handler.asInstanceOf[(REQ) => Future[Response[Body]]], requestDeserializer)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingRequestReplyHandler[REQ](routeKey)
        val d: Deserializer[REQ] = uh.deserializer
        val uid = transportManager.onCommand[REQ](requestMatcher, d, exceptionSerializer)(uh.handler)
        underlyingSubscriptions += routeKey ->(uid, uh)
      }
      r
    }
  }

  def onEvent[REQ <: Request[Body]](requestMatcher: TransportRequestMatcher,
                                    groupName: Option[String],
                                    requestDeserializer: RequestDeserializer[REQ])
                                   (handler: (REQ) => Future[Unit]): String = {

    val finalGroupName = groupName.getOrElse {
      defaultGroupName.getOrElse {
        throw new UnsupportedOperationException(s"Can't subscribe: group name is not defined")
      }
    }
    val method = requestMatcher.headers.get(Header.METHOD).map(_.specific).getOrElse(
      throw new IllegalArgumentException(s"method is not set on matcher headers: $requestMatcher")
    )
    val contentType = requestMatcher.headers.get(Header.CONTENT_TYPE).map(_.specific)
    val routeKey = getRouteKey(requestMatcher, Some(finalGroupName))

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        PubSubSubscription(handler, requestDeserializer)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingPubSubHandler[REQ](routeKey)
        val d: Deserializer[REQ] = uh.deserializer
        val uid = transportManager.onEvent[REQ](requestMatcher, finalGroupName, d)(uh.handler)
        underlyingSubscriptions += routeKey ->(uid, uh)
      }
      r
    }
  }

  def off(subscriptionId: String): Unit = {
    underlyingSubscriptions.synchronized {
      subscriptions.getRouteKeyById(subscriptionId) foreach { routeKey =>
        val cnt = subscriptions.get(routeKey).subRoutes.foldLeft(0) { (c, x) =>
          c + x._2.size
        }
        if (cnt <= 1) {
          underlyingSubscriptions.get(routeKey).foreach(k => transportManager.off(k._1))
          underlyingSubscriptions.remove(routeKey)
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
        logError("Unhandled exception", error, t)
        error.serialize(outputStream)
    }
  }

  val macroApiImpl = new MacroApi {
    def responseDeserializer(responseHeader: ResponseHeader,
                             responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                             bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body] = {
      StandardResponse(responseHeader, responseBodyJson, bodyDeserializer)
    }
  }

  protected def logError(msg: String, error: HyperBusException[ErrorBody], throwable: Throwable): Unit = {
    log.error(msg + ". #" + error.body.errorId, throwable)
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
    log.warn(safeErrorMessage("Unhandled publication", request, routeKey))
  }

  protected def unhandledException(routeKey: String, request: Request[Body], exception: Throwable): Response[Body] = {
    val errorBody = ErrorBody(DefError.INTERNAL_ERROR, Some(
      safeErrorMessage(s"Unhandled exception: ${exception.getMessage}", request, routeKey)
    ))
    log.error(errorBody.message + ". #" + errorBody.errorId, exception)
    InternalServerError(errorBody)
  }

  protected def responseSerializerNotFound(response: Response[Body]) = log.error("Can't serialize response: {}", response)

  protected def getRouteKey(requestMatcher: TransportRequestMatcher, groupName: Option[String]) = {
    if (requestMatcher.uri.isEmpty)
      throw new IllegalArgumentException(s"uri is not set on matcher: $requestMatcher")
    val specificUri = requestMatcher.uri.get.pattern.specific // todo: implement other filters?

    groupName.map {
      specificUri + "#" + _
    } getOrElse specificUri
  }

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
