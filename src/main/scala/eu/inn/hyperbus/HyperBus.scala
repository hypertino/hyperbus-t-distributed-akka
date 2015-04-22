package eu.inn.hyperbus

import java.io.InputStream

import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.impl.HyperBusMacro
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization.{RequestDecoder, RequestHeader}
import eu.inn.hyperbus.serialization.impl.Helpers
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.impl.Subscriptions
import eu.inn.servicebus.serialization.{Encoder, Decoder}
import eu.inn.servicebus.transport.HandlerResult
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{Promise, Future}
import scala.util.{Try, Random}

import scala.language.experimental.macros

class HyperBus(val underlyingBus: ServiceBus) {
  protected val subscriptions = new Subscriptions[Subscription]
  protected val randomGen = new Random()
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler)]

  protected case class Subscription(
                                     handler: (Request[Body]) => HandlerResult[Response[Body]],
                                     requestDecoder: RequestDecoder )


  protected class UnderlyingHandler(routeKey: String) {

    def unhandledRequest(request: Request[Body]): HandlerResult[Response[Body]] = {
      val s = "Unhandled request " + safe(()=>request.method) + routeKey +
        safe(()=>request.body.contentType.map("@"+_).getOrElse(""))
      log.error(s)

      val p = Promise[Response[Body]]()
      p.success(InternalError(ErrorBody(StandardErrors.HANDLER_NOT_FOUND, Some(s))))
      HandlerResult(p.future,null) //todo: exception encoder
    }

    def handler(in: Request[Body]): HandlerResult[Response[Body]] = {
      getSubscription(in).map { r =>
        r.handler(in)
      } getOrElse {
        unhandledRequest(in)
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

    val decoder: Decoder[Request[Body]] = new Object with Decoder[Request[Body]] {
      // todo: handle deserialization errors
      def decode(inputStream: InputStream): Request[Body] = {
        Helpers.decodeRequestWith(inputStream, requestDecoder)
      }

      def requestDecoder(requestHeader: RequestHeader, requestBodyJson: JsonParser): Request[Body] = {
        getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
          subscription.requestDecoder.decode(requestHeader, requestBodyJson)
        } getOrElse {
          Helpers.decodeDynamicRequest(requestHeader, requestBodyJson)
        }
      }
    }
    def safe(t:() => String): String = Try(t()).getOrElse("???")
  }

  def send[OUT <: Response[Body], IN <: Request[Body]]
    (r: IN with DefinedResponse[OUT]): Future[OUT] = {

    underlyingBus.send[OUT, IN](r.url, r, null, null)
  }

  def subscribe[IN <: Request[Body]] (groupName: Option[String])
    (handler: (IN) => Future[Response[Body]]): String = macro HyperBusMacro.subscribe[IN]

  def send[OUT <: Response[Body], IN <: Request[Body]]
    (r: IN with DefinedResponse[OUT],
    outputDecoder: Decoder[OUT],
    inputEncoder: Encoder[IN]): Future[OUT] = {

    underlyingBus.send[OUT, IN](r.url, r, inputEncoder, outputDecoder)
  }

  def subscribe
    (url: String,
     method: String,
     contentType: Option[String],
     groupName: Option[String],
     requestDecoder: RequestDecoder)
    (handler: (Request[Body]) => HandlerResult[Response[Body]]): String = {

    val routeKey = getRouteKey(url, groupName)
    val subRouteKey = getSubRouteKey(method, contentType)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(routeKey, Some(subRouteKey), Subscription(
        handler, requestDecoder
      ))

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingHandler(routeKey)
        val uid = underlyingBus.subscribe(url, groupName, uh.decoder)(uh.handler)
        underlyingSubscriptions += routeKey -> (uid, uh)
      }
      r
    }
  }

  def unsubscribe(subscriptionId: String): Unit = {
    underlyingSubscriptions.synchronized {
      subscriptions.getRouteKeyById(subscriptionId) foreach { routeKey =>
        val cnt = subscriptions.get(routeKey).foldLeft(0){ (c, x) =>
          c + x._2.size
        }
        if (cnt <= 1) {
          underlyingSubscriptions.get(routeKey).foreach(k => underlyingBus.unsubscribe(k._1))
        }
      }
      subscriptions.remove(subscriptionId)
    }
  }

  protected def getRouteKey(url: String, groupName: Option[String]) =
    groupName.map { url + "#" + _ } getOrElse url

  protected def getSubRouteKey(method: String, contentType: Option[String]) =
    contentType map (c => method + ":" + c) getOrElse method

  protected val log = LoggerFactory.getLogger(this.getClass)
}
