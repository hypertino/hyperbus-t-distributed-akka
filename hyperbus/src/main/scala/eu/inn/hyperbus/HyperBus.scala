package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.impl.Helpers
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.serialization.impl.InnerHelpers
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{PartitionArgs, SubscriptionHandlerResult, Topic}
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.{Marker, LoggerFactory}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.Try
import scala.util.control.NonFatal

/*
todo:
+ decide group result type
+ correlationId, sequenceId, replyTo
+ other headers?
+ exception when duplicate subscription
+ encode -> serialize
+ test serialize/deserialize exceptions

low priority:
  + lostResponse response log details
*/

trait HyperBusApi {
  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        requestEncoder: Encoder[REQ],
                                                        partitionArgsExtractor: PartitionArgsExtractor[REQ],
                                                        responseDecoder: ResponseDecoder[RESP]): Future[RESP]

  def publish[REQ <: Request[Body]](request: REQ,
                                    requestEncoder: Encoder[REQ],
                                    partitionArgsExtractor: PartitionArgsExtractor[REQ]): Future[Unit]

  def process[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
                                                       method: String,
                                                       contentType: Option[String],
                                                       requestDecoder: RequestDecoder[REQ],
                                                       partitionArgsExtractor: PartitionArgsExtractor[REQ])
                                                      (handler: (REQ) => SubscriptionHandlerResult[RESP]): String

  def subscribe[REQ <: Request[Body]](topic: Topic,
                                      method: String,
                                      contentType: Option[String],
                                      groupName: String,
                                      requestDecoder: RequestDecoder[REQ],
                                      partitionArgsExtractor: PartitionArgsExtractor[REQ])
                                     (handler: (REQ) => SubscriptionHandlerResult[Unit]): String

  def responseEncoder(response: Response[Body],
                      outputStream: java.io.OutputStream,
                      bodyEncoder: PartialFunction[Response[Body], Encoder[Response[Body]]]): Unit

  def responseDecoder(responseHeader: ResponseHeader,
                      responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                      bodyDecoder: PartialFunction[ResponseHeader, ResponseBodyDecoder]): Response[Body]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class HyperBus(val serviceBus: ServiceBus)(implicit val executionContext: ExecutionContext) extends HyperBusApi {
  protected trait Subscription[REQ <: Request[Body]] {
    def requestDecoder: RequestDecoder[REQ]
  }
  protected val subscriptions = new Subscriptions[SubKey, Subscription[_]]
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler[_])]
  protected val log = LoggerFactory.getLogger(this.getClass)

  def <~[REQ <: Request[Body]](request: REQ): Future[Response[Body]] = macro HyperBusMacro.ask[REQ]

  def <|[REQ <: Request[Body]](request: REQ): Future[Unit] = macro HyperBusMacro.publish[REQ]

  def |>[IN <: Request[Body]](groupName: String)
                                    (handler: (IN) => Future[Unit]): String = macro HyperBusMacro.subscribe[IN]

  def ~>[REQ <: Request[Body]](handler: (REQ) => Future[Response[Body]]): String = macro HyperBusMacro.process[REQ]

  def <~(request: DynamicRequest): Future[Response[DynamicBody]] = {
    import eu.inn.hyperbus.impl.Helpers._
    import eu.inn.hyperbus.{serialization=>hbs}
    ask(request, encodeDynamicRequest,
      extractDynamicPartitionArgs, responseDecoder(_,_,PartialFunction.empty)
    ).asInstanceOf[Future[Response[DynamicBody]]]
  }

  def <|(request: DynamicRequest): Future[Unit] = {
    import eu.inn.hyperbus.impl.Helpers._
    publish(request, encodeDynamicRequest, extractDynamicPartitionArgs)
  }


  protected case class RequestReplySubscription[REQ <: Request[Body]](
                                                                       handler: (REQ) => SubscriptionHandlerResult[Response[Body]],
                                                                       requestDecoder: RequestDecoder[REQ],
                                                                       partitionArgsExtractor: PartitionArgsExtractor[REQ]) extends Subscription[REQ]

  protected case class PubSubSubscription[REQ <: Request[Body]](
                                                                 handler: (REQ) => SubscriptionHandlerResult[Unit],
                                                                 requestDecoder: RequestDecoder[REQ],
                                                                 partitionArgsExtractor: PartitionArgsExtractor[REQ]) extends Subscription[REQ]

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

    def decoder(inputStream: InputStream): REQ = {
      try {
        InnerHelpers.decodeRequestWith[REQ](inputStream) { (requestHeader, requestBodyJson) =>
          getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
            subscription.requestDecoder(requestHeader, requestBodyJson)
          } getOrElse {
            Helpers.decodeDynamicRequest(requestHeader, requestBodyJson).asInstanceOf[REQ] // todo: why? remove and throw
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
    def handler(in: REQ): SubscriptionHandlerResult[Response[Body]] = {
      getSubscription(in) map {
        case y: RequestReplySubscription[REQ] ⇒
          val r = y.handler(in)
          val f = r.futureResult.recoverWith {
            case z: Response[_] ⇒ Future.successful(z)
            case t: Throwable ⇒ Future.successful(unhandledException(routeKey, in, t))
          }
          SubscriptionHandlerResult[Response[Body]](f, r.resultEncoder)
      } getOrElse {
        SubscriptionHandlerResult(Future.successful(unhandledRequest(routeKey, in)), defaultResponseEncoder)
      }
    }

    def partitionArgsExtractor(t: REQ): PartitionArgs = {
      getSubscription(t) map {
        case y: RequestReplySubscription[REQ] ⇒ y.partitionArgsExtractor(t)
      } getOrElse {
        PartitionArgs(Map.empty) // todo: is this ok?
      }
    }
  }

  protected class UnderlyingPubSubHandler[REQ <: Request[Body]](routeKey: String)
    extends UnderlyingHandler[REQ](routeKey) {
    def handler(in: REQ): SubscriptionHandlerResult[Unit] = {
      getSubscription(in).map {
        case s: PubSubSubscription[REQ] ⇒
          s.handler(in)
      } getOrElse {
        SubscriptionHandlerResult(Future.successful(unhandledPublication(routeKey, in)), null)
      }
    }

    def partitionArgsExtractor(t: REQ): PartitionArgs = {
      getSubscription(t) map {
        case y: PubSubSubscription[REQ] ⇒ y.partitionArgsExtractor(t)
      } getOrElse {
        PartitionArgs(Map.empty) // todo: is this ok?
      }
    }
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        requestEncoder: Encoder[REQ],
                                                        partitionArgsExtractor: PartitionArgsExtractor[REQ],
                                                        responseDecoder: ResponseDecoder[RESP]): Future[RESP] = {

    val outputDecoder = InnerHelpers.decodeResponseWith(_: InputStream)(responseDecoder)
    val args = partitionArgsExtractor(request)
    val topic = Topic(request.url, args)
    serviceBus.ask[RESP, REQ](topic, request, requestEncoder, outputDecoder) map {
      case throwable: Throwable ⇒ throw throwable
      case other ⇒ other
    }
  }

  def publish[REQ <: Request[Body]](request: REQ,
                                    requestEncoder: Encoder[REQ],
                                    partitionArgsExtractor: PartitionArgsExtractor[REQ]): Future[Unit] = {
    val args = partitionArgsExtractor(request)
    val topic = Topic(request.url, args)
    serviceBus.publish[REQ](topic, request, requestEncoder)
  }

  def process[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
                                                       method: String,
                                                       contentType: Option[String],
                                                       requestDecoder: RequestDecoder[REQ],
                                                       partitionArgsExtractor: PartitionArgsExtractor[REQ])
                                                      (handler: (REQ) => SubscriptionHandlerResult[RESP]): String = {
    val routeKey = getRouteKey(topic.url, None)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        RequestReplySubscription(handler.asInstanceOf[(REQ) => SubscriptionHandlerResult[Response[Body]]], requestDecoder, partitionArgsExtractor)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingRequestReplyHandler[REQ](routeKey)
        val d: Decoder[REQ] = uh.decoder
        val pe: PartitionArgsExtractor[REQ] = uh.partitionArgsExtractor
        val uid = serviceBus.process[Response[Body], REQ](topic, d, pe, exceptionEncoder)(uh.handler)
        underlyingSubscriptions += routeKey ->(uid, uh)
      }
      r
    }
  }

  def subscribe[REQ <: Request[Body]](topic: Topic,
                                      method: String,
                                      contentType: Option[String],
                                      groupName: String,
                                      requestDecoder: RequestDecoder[REQ],
                                      partitionArgsExtractor: PartitionArgsExtractor[REQ])
                                     (handler: (REQ) => SubscriptionHandlerResult[Unit]): String = {
    val routeKey = getRouteKey(topic.url, Some(groupName))

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        PubSubSubscription(handler, requestDecoder, partitionArgsExtractor)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingPubSubHandler[REQ](routeKey)
        val d: Decoder[REQ] = uh.decoder
        val pe: PartitionArgsExtractor[REQ] = uh.partitionArgsExtractor
        val uid = serviceBus.subscribe[REQ](topic, groupName, d, pe)(uh.handler)
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
          underlyingSubscriptions.get(routeKey).foreach(k => serviceBus.off(k._1))
        }
      }
      subscriptions.remove(subscriptionId)
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    serviceBus.shutdown(duration)
  }

  def responseEncoder(response: Response[Body],
                      outputStream: java.io.OutputStream,
                      bodyEncoder: PartialFunction[Response[Body], Encoder[Response[Body]]]): Unit = {
    if (bodyEncoder.isDefinedAt(response))
      bodyEncoder(response)(response, outputStream)
    else
      defaultResponseEncoder(response, outputStream)
  }

  protected def defaultResponseEncoder(response: Response[Body], outputStream: java.io.OutputStream): Unit = {
    import eu.inn.hyperbus.serialization._
    response.body match {
      case _: ErrorBody => createEncoder[Response[ErrorBody]](response.asInstanceOf[Response[ErrorBody]], outputStream)
      case _: DynamicCreatedBody => createEncoder[Response[DynamicCreatedBody]](response.asInstanceOf[Response[DynamicCreatedBody]], outputStream)
      case _: DynamicBody => createEncoder[Response[DynamicBody]](response.asInstanceOf[Response[DynamicBody]], outputStream)
      case _ => responseEncoderNotFound(response)
    }
  }

  protected def exceptionEncoder(exception: Throwable, outputStream: java.io.OutputStream): Unit = {
    exception match {
      case r: ErrorResponse ⇒ defaultResponseEncoder(r, outputStream)
      case t ⇒
        val error = InternalServerError(ErrorBody(DefError.INTERNAL_ERROR, Some(t.getMessage)))
        logError("Unhandled exception", error)
        defaultResponseEncoder(error, outputStream)
    }
  }

  protected def defaultResponseBodyDecoder(responseHeader: ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser): Body = {
    val decoder =
      if (responseHeader.status >= 400 && responseHeader.status <= 599)
        eu.inn.hyperbus.serialization.createResponseBodyDecoder[ErrorBody]
      else
        eu.inn.hyperbus.serialization.createResponseBodyDecoder[DynamicBody]
    decoder(responseHeader, responseBodyJson)
  }

  def responseDecoder(responseHeader: ResponseHeader,
                      responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                      bodyDecoder: PartialFunction[ResponseHeader, ResponseBodyDecoder]): Response[Body] = {
    val body =
      if (bodyDecoder.isDefinedAt(responseHeader))
        bodyDecoder(responseHeader)(responseHeader, responseBodyJson)
      else
        defaultResponseBodyDecoder(responseHeader, responseBodyJson)
    Helpers.createResponse(responseHeader, body)
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
    InternalServerError(ErrorBody(DefError.INTERNAL_ERROR, Some(
        safeErrorMessage(s"Unhandled exception: ${exception.getMessage}", request, routeKey)
      )),
      exception
    )
  }

  protected def responseEncoderNotFound(response: Response[Body]) = log.error("Can't encode response: {}", response)

  protected def getRouteKey(url: String, groupName: Option[String]) =
    groupName.map {
      url + "#" + _
    } getOrElse url

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
