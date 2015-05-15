package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.serialization.impl.Helpers
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{DefaultPosition, PublishResult, SeekPosition, SubscriptionHandlerResult}
import eu.inn.servicebus.util.Subscriptions
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
  def ask (r: Request[Body],
           requestEncoder: Encoder[Request[Body]],
           responseDecoder: ResponseDecoder): Future[Response[Body]]

  def publish(r: Request[Body],
             requestEncoder: Encoder[Request[Body]]): Future[PublishResult]

  def on(url: String,
         method: String,
         contentType: Option[String],
         requestDecoder: RequestDecoder)
        (handler: (Request[Body]) => SubscriptionHandlerResult[Response[Body]]): String

  def subscribe(url: String,
                method: String,
                contentType: Option[String],
                groupName: String,
                position: SeekPosition,
                requestDecoder: RequestDecoder)
               (handler: (Request[Body]) => SubscriptionHandlerResult[Unit]): String

  def responseEncoder(response: Response[Body],
                      outputStream: java.io.OutputStream,
                      bodyEncoder: PartialFunction[Response[Body],ResponseEncoder]): Unit

  def responseDecoder(responseHeader: ResponseHeader,
                     responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                     bodyDecoder: PartialFunction[ResponseHeader,ResponseBodyDecoder]): Response[Body]
}

class HyperBus(val underlyingBus: ServiceBus)(implicit val executionContext: ExecutionContext) extends HyperBusBase {
  protected val subscriptions = new Subscriptions[Subscription]
  protected val randomGen = new Random()
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler)]
  protected val log = LoggerFactory.getLogger(this.getClass)

  protected trait Subscription {
    def requestDecoder: RequestDecoder
  }
  protected case class RequestReplySubscription(
                                   handler: (Request[Body]) => SubscriptionHandlerResult[Response[Body]],
                                   requestDecoder: RequestDecoder ) extends Subscription
  protected case class PubSubSubscription(
                                     handler: (Request[Body]) => SubscriptionHandlerResult[Unit],
                                     requestDecoder: RequestDecoder ) extends Subscription

  protected abstract class UnderlyingHandler(routeKey: String) {
    protected def getSubscription(in: Request[Body]): Option[Subscription] = getSubscription(in.method, in.body.contentType)

    protected def getSubscription(method:String, contentType: Option[String]): Option[Subscription] = {
      val subRouteKey = getSubRouteKey(method, contentType)

      subscriptions.get(routeKey).get(subRouteKey).orElse{
        subscriptions.get(routeKey).get(getSubRouteKey(method, None))
      } map { subscrSeq =>
        val idx = if (subscrSeq.size > 1) {
          randomGen.nextInt(subscrSeq.size)
        } else {
          0
        }
        subscrSeq(idx).subscription
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

  protected class UnderlyingRequestReplyHandler(routeKey: String) extends UnderlyingHandler(routeKey) {
    def handler(in: Request[Body]): SubscriptionHandlerResult[Response[Body]] = {
      getSubscription(in).map {
        case s: RequestReplySubscription ⇒
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

  def ask
    (r: Request[Body],
      requestEncoder: Encoder[Request[Body]],
      responseDecoder: ResponseDecoder): Future[Response[Body]] = {

    val outputDecoder = Helpers.decodeResponseWith(_:InputStream)(responseDecoder)
    underlyingBus.ask[Response[Body], Request[Body]](r.url, r, requestEncoder, outputDecoder) map {
      case throwable: Throwable ⇒ throw throwable
      case r: Response[Body] ⇒ r
    }
  }

  def ![IN <: Request[Body]](r: IN): Future[PublishResult] = macro HyperBusMacro.publish[IN]

  def publish(r: Request[Body],
              requestEncoder: Encoder[Request[Body]]): Future[PublishResult] = {
    underlyingBus.publish[Request[Body]](r.url, r, requestEncoder)
  }

  def on[IN <: Request[_ <: Body]](handler: (IN) => Future[Response[Body]]): String = macro HyperBusMacro.on[IN]

  def on(url: String,
         method: String,
         contentType: Option[String],
         requestDecoder: RequestDecoder)
        (handler: (Request[Body]) => SubscriptionHandlerResult[Response[Body]]): String = {
    val routeKey = getRouteKey(url, None)
    val subRouteKey = getSubRouteKey(method, contentType)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        Some(subRouteKey),
        RequestReplySubscription(handler, requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingRequestReplyHandler(routeKey)
        val uid = underlyingBus.on(url, uh.decoder)(uh.handler)
        underlyingSubscriptions += routeKey -> (uid, uh)
      }
      r
    }
  }

  def subscribe(url: String,
                method: String,
                contentType: Option[String],
                groupName: String,
                position: SeekPosition,
                requestDecoder: RequestDecoder)
               (handler: (Request[Body]) => SubscriptionHandlerResult[Unit]): String = {
    val routeKey = getRouteKey(url, Some(groupName))
    val subRouteKey = getSubRouteKey(method, contentType)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        Some(subRouteKey),
        PubSubSubscription(handler, requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingPubSubHandler(routeKey)
        val uid = underlyingBus.subscribe(url, groupName, position, uh.decoder)(uh.handler)
        underlyingSubscriptions += routeKey -> (uid, uh)
      }
      r
    }
  }

  def subscribe[IN <: Request[_ <: Body]](groupName: String, position: SeekPosition)
                                         (handler: (IN) => Future[Unit]): String = macro HyperBusMacro.subscribe[IN]

  def off(subscriptionId: String): Unit = {
    underlyingSubscriptions.synchronized {
      subscriptions.getRouteKeyById(subscriptionId) foreach { routeKey =>
        val cnt = subscriptions.get(routeKey).foldLeft(0){ (c, x) =>
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
      case _: DynamicBody => eu.inn.hyperbus.serialization.createEncoder[Response[DynamicBody]](response.asInstanceOf[Response[DynamicBody]], outputStream)
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

  protected def getSubRouteKey(method: String, contentType: Option[String]) =
    contentType map (c => method + ":" + c) getOrElse method

  protected def safe(t:() => String): String = Try(t()).getOrElse("???")
}
