package eu.inn.hyperbus

import java.io.InputStream

import com.fasterxml.jackson.core.JsonParser
import eu.inn.binders.dynamic.Value
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.serialization.impl.Helpers
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.serialization.Encoder
import eu.inn.servicebus.transport.SubscriptionHandlerResult
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.{Random, Try}

/*
todo:
+ decide group result type
+ correlationId, sequenceId, replyTo
+ other headers?
+ exception when duplicate subscription
+ encode -> serialize

low priority:
  + lostResponse response log details
*/

trait HyperBusBase {
  def ask (r: Request[Body],
           requestEncoder: Encoder[Request[Body]],
           responseDecoder: ResponseDecoder): Future[Response[Body]]

  def on(url: String,
         method: String,
         contentType: Option[String],
         groupName: Option[String],
         requestDecoder: RequestDecoder)
        (handler: (Request[Body]) => SubscriptionHandlerResult[Response[Body]]): String

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

  protected case class Subscription(
                                   handler: (Request[Body]) => SubscriptionHandlerResult[Response[Body]],
                                   requestDecoder: RequestDecoder )


  protected class UnderlyingHandler(routeKey: String) {
    def handler(in: Request[Body]): SubscriptionHandlerResult[Response[Body]] = {
      getSubscription(in).map { s ⇒
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
      // todo: handle deserialization errors
      Helpers.decodeRequestWith(inputStream) { (requestHeader, requestBodyJson) =>
        getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
          subscription.requestDecoder(requestHeader, requestBodyJson)
        } getOrElse {
          decodeDynamicRequest(requestHeader, requestBodyJson)
        }
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

  def on[IN <: Request[_ <: Body]] (groupName: Option[String] = None)
                                     (handler: (IN) => Future[Response[Body]]): String = macro HyperBusMacro.on[IN]

  def on(url: String,
         method: String,
         contentType: Option[String],
         groupName: Option[String],
         requestDecoder: RequestDecoder)
        (handler: (Request[Body]) => SubscriptionHandlerResult[Response[Body]]): String = {
    // todo: handle service exceptions

    val routeKey = getRouteKey(url, groupName)
    val subRouteKey = getSubRouteKey(method, contentType)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        Some(subRouteKey),
        Subscription(handler.asInstanceOf[(Request[Body]) => SubscriptionHandlerResult[Response[Body]]], requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingHandler(routeKey)
        val uid = underlyingBus.on(url, groupName, uh.decoder)(uh.handler)
        underlyingSubscriptions += routeKey -> (uid, uh)
      }
      r
    }
  }

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

  protected def decodeDynamicRequest(requestHeader: RequestHeader, jsonParser: JsonParser): Request[Body] = {
    val body = SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      DynamicBody(deserializer.unbind[Value], requestHeader.contentType)
    }

    requestHeader.method match {
      case StandardMethods.GET => DynamicGet(requestHeader.url, body)
      case StandardMethods.POST => DynamicPost(requestHeader.url, body)
      case StandardMethods.PUT => DynamicPut(requestHeader.url, body)
      case StandardMethods.DELETE => DynamicDelete(requestHeader.url, body)
      case StandardMethods.PATCH => DynamicPatch(requestHeader.url, body)
      case _ => {
        val s = s"Unknown method: '${requestHeader.method}'" //todo: save more details (messageId)
        log.error(s)
        throw new DecodeException(s)
      }
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
    //todo: response visitor
    //todo: responsDecoder merge with responseBodyDecoder
    // todo: Generic Errors and Responses

    val body =
      if (bodyDecoder.isDefinedAt(responseHeader))
        bodyDecoder(responseHeader)(responseHeader,responseBodyJson)
      else
        defaultResponseBodyDecoder(responseHeader,responseBodyJson)

    responseHeader.status match {
      case Status.OK => Ok(body)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody])
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBodyTrait])
      case Status.INTERNAL_ERROR => InternalError(body.asInstanceOf[ErrorBodyTrait])
    }
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

  def unhandledException(routeKey: String, request: Request[Body], exception: Throwable): Future[Response[Body]] = {
    val s = safeLogError("Unhandled exception", request, routeKey)
    Future.successful {
      InternalError(ErrorBody(StandardErrors.INTERNAL_ERROR, Some(s)))
    }
  }

  protected def responseEncoderNotFound(response: Response[Body]) = log.error("Can't encode: {}", response)

  protected def getRouteKey(url: String, groupName: Option[String]) =
    groupName.map { url + "#" + _ } getOrElse url

  protected def getSubRouteKey(method: String, contentType: Option[String]) =
    contentType map (c => method + ":" + c) getOrElse method

  protected def safe(t:() => String): String = Try(t()).getOrElse("???")
}
