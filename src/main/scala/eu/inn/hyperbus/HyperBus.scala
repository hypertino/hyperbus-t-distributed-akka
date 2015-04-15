package eu.inn.hyperbus

import eu.inn.hyperbus.protocol._

import scala.concurrent.Future

class HyperBus(val underlyingBus: ServiceBus) {

  def send[OUT <: Response[_],IN <: Request[_]]
    (r: IN with DefinedResponse[OUT]): Future[OUT] = ???

  def subscribe[OUT/* <: Response[_]*/,IN <: Request[_]] (
                                                        groupName: Option[String],
                                                        handler: (IN) => Future[OUT]
                                                        ): String = ???

  def unsubscribe(subscriptionId: String): Unit = underlyingBus.unsubscribe(subscriptionId)
}

