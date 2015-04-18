package eu.inn.hyperbus

import eu.inn.hyperbus.protocol._
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.impl.Subscriptions
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{Promise, Future}
import scala.util.Random

class HyperBus(val underlyingBus: ServiceBus) {
  protected val subscriptions = new Subscriptions[Subscription]
  protected val randomGen = new Random()
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler)]

  protected case class Subscription(handler: (Request[_]) => Future[Response[_]])

  protected class UnderlyingHandler(routeKey: String) {
    def handler[B <: Body](in: Request[B]): Future[Response[_]] = {
      val subRouteKey = getSubRouteKey(in.method, in.body.contentType)
      subscriptions.get(routeKey).get(subRouteKey) map { subscrSeq =>
        val idx = if (subscrSeq.size > 1) {
          randomGen.nextInt(subscrSeq.size)
        } else {
          0
        }
        val subscr = subscrSeq(idx)
        subscr.subcription.handler(in)
      } getOrElse {
        val s = s"Unhandled request $subRouteKey:$routeKey"
        log.error(s)

        val p = Promise[Response[_]]()
        p.success(InternalError(ErrorBody(StandardErrors.HANDLER_NOT_FOUND, Some(s))))
        p.future
      }
    }
  }

  def send[OUT <: Response[_],IN <: Request[_]]
    (r: IN with DefinedResponse[OUT]): Future[OUT] = {

    underlyingBus.send[OUT, IN](r.url, r, null, null)
  }

  def subscribe (groupName: Option[String],
                 handler: (Request[_]) => Future[Response[_]]): String = {

    //underlyingBus.subscribe[OUT,IN]("/resources", groupName, null, null, handler)

    subscribe("/resource", "get", None, groupName, handler)
  }

  def subscribe[A,B](url: String,
                method: String,
                contentType: Option[String],
                groupName: Option[String],
                handler: (Request[A]) => Future[Response[B]]
                 ): String = {

    val routeKey = getRouteKey(url, groupName)
    val subRouteKey = getSubRouteKey(method, contentType)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(routeKey, Some(subRouteKey), Subscription(handler))

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingHandler(routeKey)
        val uid = underlyingBus.subscribe(url, groupName, null, null, uh.handler)
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

