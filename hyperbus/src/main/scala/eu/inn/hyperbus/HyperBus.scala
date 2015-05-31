package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.standard.{DefError, DynamicCreatedBody, InternalServerError}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.serialization.impl.Helpers
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{PartitionArgs, PublishResult, SubscriptionHandlerResult, Topic}
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.collection.mutable
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
  def ask[RESP <: Response[Body], REQ <: Request[Body]](r: REQ,
                                                        requestEncoder: Encoder[REQ],
                                                        partitionArgsExtractor: PartitionArgsExtractor[REQ],
                                                        responseDecoder: ResponseDecoder[RESP]): Future[RESP]

  def publish[REQ <: Request[Body]](r: REQ,
                                    requestEncoder: Encoder[REQ],
                                    partitionArgsExtractor: PartitionArgsExtractor[REQ]): Future[PublishResult]

  def on[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
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
}

class HyperBus(val underlyingBus: ServiceBus)(implicit val executionContext: ExecutionContext) extends HyperBusApi {
  protected val subscriptions = new Subscriptions[SubKey, Subscription[_]]
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler[_])]
  protected val log = LoggerFactory.getLogger(this.getClass)

  def ?[REQ <: Request[Body]](r: REQ): Future[Response[Body]] = macro HyperBusMacro.ask[REQ]

  def ![REQ <: Request[Body]](r: REQ): Future[PublishResult] = macro HyperBusMacro.publish[REQ]

  def subscribe[IN <: Request[Body]](groupName: String)
                                    (handler: (IN) => Future[Unit]): String = macro HyperBusMacro.subscribe[IN]

  def on[REQ <: Request[Body]](handler: (REQ) => Future[Response[Body]]): String = macro HyperBusMacro.on[REQ]

  protected trait Subscription[REQ <: Request[Body]] {
    def requestDecoder: RequestDecoder[REQ]
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
        Helpers.decodeRequestWith[REQ](inputStream) { (requestHeader, requestBodyJson) =>
          getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
            subscription.requestDecoder(requestHeader, requestBodyJson)
          } getOrElse {
            HyperBusUtils.decodeDynamicRequest(requestHeader, requestBodyJson).asInstanceOf[REQ] // todo: why? remove and throw
          }
        }
      }
      catch {
        case NonFatal(e) =>
          log.error("Can't decode request", e)
          throw e
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
            case t: Throwable ⇒ unhandledException(routeKey, in, t)
          }
          SubscriptionHandlerResult[Response[Body]](f, r.resultEncoder)
      } getOrElse {
        SubscriptionHandlerResult(unhandledRequest(routeKey, in), defaultResponseEncoder)
      }
    }

    def partitionArgsExtractor(t: REQ): PartitionArgs = {
      getSubscription(t) map {
        case y: RequestReplySubscription[REQ] ⇒ y.partitionArgsExtractor(t)
      } getOrElse {
        PartitionArgs(Map()) // todo: is this ok?
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
        SubscriptionHandlerResult(unhandledPublication(routeKey, in), null)
      }
    }

    def partitionArgsExtractor(t: REQ): PartitionArgs = {
      getSubscription(t) map {
        case y: PubSubSubscription[REQ] ⇒ y.partitionArgsExtractor(t)
      } getOrElse {
        PartitionArgs(Map()) // todo: is this ok?
      }
    }
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](r: REQ,
                                                        requestEncoder: Encoder[REQ],
                                                        partitionArgsExtractor: PartitionArgsExtractor[REQ],
                                                        responseDecoder: ResponseDecoder[RESP]): Future[RESP] = {

    val outputDecoder = Helpers.decodeResponseWith(_: InputStream)(responseDecoder)
    val args = partitionArgsExtractor(r)
    val topic = Topic(r.url, args)
    underlyingBus.ask[RESP, REQ](topic, r, requestEncoder, outputDecoder) map {
      case throwable: Throwable ⇒ throw throwable
      case other ⇒ other
    }
  }

  def publish[REQ <: Request[Body]](r: REQ,
                                    requestEncoder: Encoder[REQ],
                                    partitionArgsExtractor: PartitionArgsExtractor[REQ]): Future[PublishResult] = {
    val args = partitionArgsExtractor(r)
    val topic = Topic(r.url, args)
    underlyingBus.publish[REQ](topic, r, requestEncoder)
  }

  def on[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
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
        val uid = underlyingBus.on[Response[Body], REQ](topic, d, pe)(uh.handler)
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
        val uid = underlyingBus.subscribe[REQ](topic, groupName, d, pe)(uh.handler)
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
          underlyingSubscriptions.get(routeKey).foreach(k => underlyingBus.off(k._1))
        }
      }
      subscriptions.remove(subscriptionId)
    }
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
      case _: DynamicBodyContainer => createEncoder[Response[DynamicBodyContainer]](response.asInstanceOf[Response[DynamicBodyContainer]], outputStream)
      case _ => responseEncoderNotFound(response)
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
    HyperBusUtils.createResponse(responseHeader, body)
  }

  def safeLogError(msg: String, request: Request[Body], routeKey: String, error: Throwable = null): String = {
    val s = msg + " " + safe(() => request.method) + routeKey +
      safe(() => request.body.contentType.map("@" + _).getOrElse(""))
    log.error(s, error)
    s
  }

  def unhandledRequest(routeKey: String, request: Request[Body]): Future[Response[Body]] = {
    val s = safeLogError("Unhandled request", request, routeKey)
    Future.successful {
      InternalServerError(ErrorBody(DefError.HANDLER_NOT_FOUND, Some(s)))
    }
  }

  def unhandledPublication(routeKey: String, request: Request[Body]): Future[Unit] = {
    val s = safeLogError("Unhandled request", request, routeKey)
    Future.successful {}
  }

  def unhandledException(routeKey: String, request: Request[Body], exception: Throwable): Future[Response[Body]] = {
    val s = safeLogError("Unhandled exception", request, routeKey)
    Future.successful {
      InternalServerError(ErrorBody(DefError.INTERNAL_ERROR, Some(s)))
    }
  }

  protected def responseEncoderNotFound(response: Response[Body]) = log.error("Can't encode response: {}", response)

  protected def getRouteKey(url: String, groupName: Option[String]) =
    groupName.map {
      url + "#" + _
    } getOrElse url

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
