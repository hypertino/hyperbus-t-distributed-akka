package eu.inn.hyperbus

import eu.inn.hyperbus.protocol._
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.impl.Subscriptions

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

private [hyperbus] case class Subscription[OUT,IN](handler: IN => Future[OUT])

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

//  def subscribe[OUT/* <: Response[_]*/,IN <: Request[_]] (
//                                                           url: String,
//                                                           method: String,
//                                                           contentType: Option[String],
//                                                           groupName: Option[String],
//                                                           handler: (IN) => Future[OUT]
//                                                           ): String = {
//
//
//
//    val subscription = Subscription(handler)
//    val id = underlyingBus.subscribe[OUT,IN](url, groupName, null, null, handler)
//
//
//  }

  def unsubscribe(subscriptionId: String): Unit = underlyingBus.unsubscribe(subscriptionId)
}

