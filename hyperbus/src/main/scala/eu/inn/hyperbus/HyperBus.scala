package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.serialization.impl.Helpers
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{Topic, PublishResult, SubscriptionHandlerResult}
import eu.inn.servicebus.util.{Subscriptions}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.control.NonFatal
import scala.util.{Random, Try}

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

trait HyperBusBase {
  def ask[RESP <: Response[Body], REQ <: Request[Body]] (r: REQ,
           requestEncoder: Encoder[REQ],
           partitionArgsExtractor: PartitionArgsExtractor[REQ],
           responseDecoder: ResponseDecoder): Future[RESP]

  def publish[REQ <: Request[Body]](r: REQ,
              requestEncoder: Encoder[REQ],
              partitionArgsExtractor: PartitionArgsExtractor[REQ]): Future[PublishResult]

  def on[RESP<: Response[Body], REQ <: Request[Body]](topic: Topic,
         method: String,
         contentType: Option[String],
         requestDecoder: RequestDecoder,
         partitionArgsExtractor: PartitionArgsExtractor[REQ])
        (handler: (REQ) => SubscriptionHandlerResult[RESP]): String

  def subscribe[REQ <: Request[Body]](topic: Topic,
                method: String,
                contentType: Option[String],
                groupName: String,
                requestDecoder: RequestDecoder,
                partitionArgsExtractor: PartitionArgsExtractor[REQ])
               (handler: (REQ) => SubscriptionHandlerResult[Unit]): String

  def responseEncoder(response: Response[Body],
                      outputStream: java.io.OutputStream,
                      bodyEncoder: PartialFunction[Response[Body],ResponseEncoder]): Unit

  def responseDecoder(responseHeader: ResponseHeader,
                     responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                     bodyDecoder: PartialFunction[ResponseHeader,ResponseBodyDecoder]): Response[Body]
}

class HyperBus(val underlyingBus: ServiceBus)(implicit val executionContext: ExecutionContext) extends HyperBusBase {
  protected val subscriptions = new Subscriptions[SubKey,Subscription[_]]
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler)]
  protected val log = LoggerFactory.getLogger(this.getClass)

  protected trait Subscription[REQ <: Request[Body]] {
    def requestDecoder: RequestDecoder
  }
  protected case class RequestReplySubscription[RESP <: Response[Body], REQ <: Request[Body]](
                                   handler: (REQ) => SubscriptionHandlerResult[RESP],
                                   requestDecoder: RequestDecoder ) extends Subscription[REQ]

  protected case class PubSubSubscription[REQ <: Request[Body]](
                                     handler: (REQ) => SubscriptionHandlerResult[Unit],
                                     requestDecoder: RequestDecoder ) extends Subscription[REQ]

  protected case class SubKey(method: String, contentType: Option[String])

  protected abstract class UnderlyingHandler[REQ <: Request[Body]](routeKey: String) {
    protected def getSubscription(in: REQ): Option[Subscription[REQ]] = getSubscription(in.method, in.body.contentType)

    protected def getSubscription(method:String, contentType: Option[String]): Option[Subscription[REQ]] = {
      subscriptions.get(routeKey).subRoutes.get(SubKey(method, contentType)).orElse{  // at first try exact match
        subscriptions.get(routeKey).subRoutes.get(SubKey(method, None))               // at second without contentType
      } map { subscriptionList =>
        subscriptionList.getRandomSubscription.asInstanceOf[Subscription[REQ]]
      }
    }

    def decoder(inputStream: InputStream): Request[Body] = {
      try {
        Helpers.decodeRequestWith(inputStream) { (requestHeader, requestBodyJson) =>
          getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
            subscription.requestDecoder(requestHeader, requestBodyJson)
          } getOrElse {
            HyperBusUtils.decodeDynamicRequest(requestHeader, requestBodyJson)
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

  protected class UnderlyingRequestReplyHandler[RESP <: Response[Body], REQ <: Request[Body]](routeKey: String)
    extends UnderlyingHandler[REQ](routeKey) {
    def handler(in: REQ): SubscriptionHandlerResult[RESP] = {
      getSubscription(in).map {
        case s: RequestReplySubscription[RESP,REQ] ⇒
          val r = s.handler(in)
          val f = r.futureResult.recoverWith {
            case x: Response[_] ⇒ Future.successful(x)
            case t: Throwable ⇒ unhandledException(routeKey,in, t)
          }
          SubscriptionHandlerResult(f, r.resultEncoder)
      } getOrElse {
        SubscriptionHandlerResult(unhandledRequest(routeKey, in), defaultResponseEncoder)
      }
    }
  }

  protected class UnderlyingPubSubHandler(routeKey: String) extends UnderlyingHandler(routeKey) {
    def handler(in: Request[Body]): SubscriptionHandlerResult[Unit] = {
      getSubscription(in).map {
        case s: PubSubSubscription ⇒
          s.handler(in)
      } getOrElse {
        SubscriptionHandlerResult(unhandledPublication(routeKey, in), null)
      }
    }
  }

  def ?[IN <: Request[Body]](r: IN): Future[Response[Body]] = macro HyperBusMacro.ask[IN]

  def ask[OUT <: Response[Body], IN <: Request[Body]] (r: IN,
                                                       requestEncoder: Encoder[IN],
                                                       partitionArgsExtractor: PartitionArgsExtractor[IN],
                                                       responseDecoder: ResponseDecoder): Future[OUT] = {

    val outputDecoder = Helpers.decodeResponseWith(_:InputStream)(responseDecoder)
    val args = partitionArgsExtractor(r)
    val topic = Topic(r.url, args)
    underlyingBus.ask[OUT,IN](topic, r, requestEncoder, outputDecoder.asInstanceOf[Decoder[OUT]]) map {
      case throwable: Throwable ⇒ throw throwable
      case other ⇒ other
    }
  }

  def ![IN <: Request[Body]](r: IN): Future[PublishResult] = macro HyperBusMacro.publish[IN]

  def publish[IN <: Request[Body]](r: IN,
                                   requestEncoder: Encoder[IN],
                                   partitionArgsExtractor: PartitionArgsExtractor[IN]): Future[PublishResult] = {
    val args = partitionArgsExtractor(r)
    val topic = Topic(r.url, args)
    underlyingBus.publish[IN](topic, r, requestEncoder)
  }

  def on[IN <: Request[_ <: Body]](handler: (IN) => Future[Response[Body]]): String = macro HyperBusMacro.on[IN]

  def on[RESP<: Response[Body], REQ <: Request[Body]](topic: Topic,
                                                      method: String,
                                                      contentType: Option[String],
                                                      requestDecoder: RequestDecoder,
                                                      partitionArgsExtractor: PartitionArgsExtractor[REQ])
                                                     (handler: (REQ) => SubscriptionHandlerResult[RESP]): String = {
    val routeKey = getRouteKey(topic.url, None)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        RequestReplySubscription(handler.asInstanceOf[(Request[Body]) => SubscriptionHandlerResult[Response[Body]]], requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingRequestReplyHandler(routeKey)
        val d: Decoder[Request[Body]] = uh.decoder
        val h: (Request[Body]) => SubscriptionHandlerResult[Response[Body]] = uh.handler
        val uid = underlyingBus.on[RESP,REQ](topic, d.asInstanceOf[(InputStream) ⇒ REQ], partitionArgsExtractor)(h.asInstanceOf[(REQ) => SubscriptionHandlerResult[RESP]])

        underlyingSubscriptions += routeKey -> (uid, uh)
      }
      r
    }
  }

  def subscribe[REQ <: Request[Body]](topic: Topic,
                                      method: String,
                                      contentType: Option[String],
                                      groupName: String,
                                      requestDecoder: RequestDecoder,
                                      partitionArgsExtractor: PartitionArgsExtractor[REQ])
                                     (handler: (REQ) => SubscriptionHandlerResult[Unit]): String = {
    val routeKey = getRouteKey(topic.url, Some(groupName))

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        PubSubSubscription(handler.asInstanceOf[(Request[Body]) => SubscriptionHandlerResult[Unit]], requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingPubSubHandler(routeKey)
        val d: Decoder[Request[Body]] = uh.decoder
        val h: (Request[Body]) => SubscriptionHandlerResult[Unit] = uh.handler
        val uid = underlyingBus.subscribe[REQ](topic, groupName, d.asInstanceOf[(InputStream) ⇒ REQ], partitionArgsExtractor.asInstanceOf)(h.asInstanceOf[(REQ) => SubscriptionHandlerResult[Unit]])
        underlyingSubscriptions += routeKey -> (uid, uh)
      }
      r
    }
  }

  def subscribe[IN <: Request[_ <: Body]](groupName: String)
                                         (handler: (IN) => Future[Unit]): String = macro HyperBusMacro.subscribe[IN]

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
                      bodyEncoder: PartialFunction[Response[Body],ResponseEncoder]): Unit = {
    if (bodyEncoder.isDefinedAt(response))
      bodyEncoder(response)(response, outputStream)
    else
      defaultResponseEncoder(response, outputStream)
  }

  protected def defaultResponseEncoder(response: Response[Body], outputStream: java.io.OutputStream): Unit = {
    response.body match {
      case _: ErrorBody => eu.inn.hyperbus.serialization.createEncoder[Response[ErrorBody]](response.asInstanceOf[Response[ErrorBody]], outputStream)
      case _: CreatedDynamicBody => eu.inn.hyperbus.serialization.createEncoder[Response[CreatedDynamicBody]](response.asInstanceOf[Response[CreatedDynamicBody]], outputStream)
      case _: DefaultDynamicBody => eu.inn.hyperbus.serialization.createEncoder[Response[DefaultDynamicBody]](response.asInstanceOf[Response[DefaultDynamicBody]], outputStream)
      case _ => responseEncoderNotFound(response)
    }
  }

  protected def defaultResponseBodyDecoder(responseHeader: ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser): Body = {
    val decoder =
      if (responseHeader.status >= 400 && responseHeader.status <= 599)
        eu.inn.hyperbus.serialization.createResponseBodyDecoder[ErrorBody]
      else
        eu.inn.hyperbus.serialization.createResponseBodyDecoder[DynamicBody]
    decoder(responseHeader,responseBodyJson)
  }

  def responseDecoder(responseHeader: ResponseHeader,
                     responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                     bodyDecoder: PartialFunction[ResponseHeader,ResponseBodyDecoder]): Response[Body] = {
    val body =
      if (bodyDecoder.isDefinedAt(responseHeader))
        bodyDecoder(responseHeader)(responseHeader,responseBodyJson)
      else
        defaultResponseBodyDecoder(responseHeader,responseBodyJson)
    HyperBusUtils.createResponse(responseHeader, body)
  }

  def safeLogError(msg: String, request: Request[Body], routeKey: String, error: Throwable = null): String = {
    val s = msg + " " + safe(()=>request.method) + routeKey +
      safe(()=>request.body.contentType.map("@"+_).getOrElse(""))
    log.error(s, error)
    s
  }

  def unhandledRequest(routeKey: String, request: Request[Body]): Future[Response[Body]] = {
    val s = safeLogError("Unhandled request", request, routeKey)
    Future.successful {
      InternalError(ErrorBody(StandardErrors.HANDLER_NOT_FOUND, Some(s)))
    }
  }

  def unhandledPublication(routeKey: String, request: Request[Body]): Future[Unit] = {
    val s = safeLogError("Unhandled request", request, routeKey)
    Future.successful{}
  }

  def unhandledException(routeKey: String, request: Request[Body], exception: Throwable): Future[Response[Body]] = {
    val s = safeLogError("Unhandled exception", request, routeKey)
    Future.successful {
      InternalError(ErrorBody(StandardErrors.INTERNAL_ERROR, Some(s)))
    }
  }

  protected def responseEncoderNotFound(response: Response[Body]) = log.error("Can't encode response: {}", response)

  protected def getRouteKey(url: String, groupName: Option[String]) =
    groupName.map { url + "#" + _ } getOrElse url

  protected def safe(t:() => String): String = Try(t()).getOrElse("???")
}
