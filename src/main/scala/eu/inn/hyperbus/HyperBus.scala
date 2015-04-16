package eu.inn.hyperbus

import eu.inn.hyperbus.protocol._
import eu.inn.servicebus.ServiceBus

import scala.concurrent.Future

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

  def unsubscribe(subscriptionId: String): Unit = underlyingBus.unsubscribe(subscriptionId)
}

