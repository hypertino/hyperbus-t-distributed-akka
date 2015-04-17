package eu.inn.hyperbus

import eu.inn.hyperbus.protocol._
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.impl.Subscriptions

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

private [hyperbus] class Subscription[OUT,IN <: Request[Body]]
  (val subscriptions: TrieMap[String, (IN) => Future[OUT]]) {

  def innerHandler(in: IN with DynamicRequest): Future[OUT] = {
    val key: String = in.body.contentType map (c => in.method + ":" + c) getOrElse in.method

    subscriptions.get(key).map { h=>
      h(in)
    } getOrElse {
      throw new RuntimeException("yoyo") // todo: unhandled messages
    }
  }
}

class HyperBus(val underlyingBus: ServiceBus) {

  def send[OUT <: Response[_],IN <: Request[_]]
    (r: IN with DefinedResponse[OUT]): Future[OUT] = {

    underlyingBus.send[OUT, IN](r.url, r, null, null)
  }

  def subscribe[OUT/* <: Response[_]*/,IN <: Request[_]] (
                                                        groupName: Option[String],
                                                        handler: (IN) => Future[OUT]
                                                        ): String = {

    underlyingBus.subscribe[OUT,IN]("/resources", groupName, null, null, handler)
  }

  val subscriptions: TrieMap[]

  def subscribe[OUT/* <: Response[_]*/,IN <: Request[_]] (
                                                           url: String,
                                                           method: String,
                                                           contentType: Option[String],
                                                           groupName: Option[String],
                                                           handler: (IN) => Future[OUT]
                                                           ): String = {



    //val subscription = Subscription(handler)
    //val id = underlyingBus.subscribe[OUT,IN](url, groupName, null, null, handler)

    //id
    ""
  }

  def unsubscribe(subscriptionId: String): Unit = underlyingBus.unsubscribe(subscriptionId)
}

