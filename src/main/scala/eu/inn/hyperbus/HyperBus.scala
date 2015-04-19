package eu.inn.hyperbus

import eu.inn.hyperbus.protocol._
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.impl.{SubscriptionWithId, Subscriptions}
import eu.inn.servicebus.serialization.{Encoder, Decoder}
import eu.inn.servicebus.transport.HandlerResult
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{Promise, Future}
import scala.util.{Try, Random}

class HyperBus(val underlyingBus: ServiceBus) {
  protected val subscriptions = new Subscriptions[Subscription]
  protected val randomGen = new Random()
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler)]

  protected case class Subscription(
                                     handler: (Request[Body]) => HandlerResult[Response[Body]],
                                     requestDecoder: Decoder[Request[Body]] )

  protected class UnderlyingHandler(routeKey: String) {

    def handler(in: Request[Body]): HandlerResult[Response[Body]] = {
      getSubscription(in) map { subscrSeq =>
        val idx = if (subscrSeq.size > 1) {
          randomGen.nextInt(subscrSeq.size)
        } else {
          0
        }
        subscrSeq(idx).subcription.handler(in)

      } getOrElse {

        val s = "Unhandled request " + safe(()=>in.method) + routeKey +
          safe(()=>in.body.contentType.map("@"+_).getOrElse(""))
        log.error(s)

        val p = Promise[Response[Body]]()
        p.success(InternalError(ErrorBody(StandardErrors.HANDLER_NOT_FOUND, Some(s))))
        HandlerResult(p.future,null) //todo: encoder
      }
    }

    protected def getSubscription(in: Request[Body]): Option[IndexedSeq[SubscriptionWithId[Subscription]]] = {
      val ct = in.body.contentType
      val subRouteKey = getSubRouteKey(in.method, ct)

      subscriptions.get(routeKey).get(subRouteKey).orElse{
        subscriptions.get(routeKey).get(getSubRouteKey(in.method, None))
      }
    }

    val encoder: Encoder[Response[Body]] = null
    val decoder: Decoder[Request[Body]] = null
    def safe(t:() => String): String = Try(t()).getOrElse("???")
  }

  def send[OUT <: Response[Body], IN <: Request[Body]]
    (r: IN with DefinedResponse[OUT]): Future[OUT] = {

    underlyingBus.send[OUT, IN](r.url, r, null, null)
  }

  // todo: this is macro
  def subscribe[OUT <: Response[Body], IN <: Request[Body]]
    (groupName: Option[String], handler: (IN) => Future[OUT]): String = {
    subscribe("/resources", "post", None, groupName,
      null, // todo: decoder
      ((in: Request[Body]) => {
        HandlerResult(handler(in.asInstanceOf[IN]), null) //todo: encoder
      })
    )
  }

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
     requestDecoder: Decoder[Request[Body]],
     handler: (Request[Body]) => HandlerResult[Response[Body]]): String = {

    val routeKey = getRouteKey(url, groupName)
    val subRouteKey = getSubRouteKey(method, contentType)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(routeKey, Some(subRouteKey), Subscription(
        handler, requestDecoder
      ))

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingHandler(routeKey)
        val uid = underlyingBus.subscribe(url, groupName, uh.decoder, uh.handler)
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
