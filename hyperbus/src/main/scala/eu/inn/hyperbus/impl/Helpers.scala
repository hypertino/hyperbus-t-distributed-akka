package eu.inn.hyperbus.impl

import eu.inn.hyperbus.protocol.{Body, Request, Response}
import eu.inn.hyperbus.serialization.RequestDecoder
import eu.inn.servicebus.serialization.Encoder
import eu.inn.servicebus.transport.SubscriptionHandlerResult

import scala.concurrent.Future

object Helpers {

  def wrapHandler[OUT <: Response[Body], IN <: Request[Body]](handler: (IN => Future[OUT]), encoder: Encoder[OUT]): (IN) => SubscriptionHandlerResult[Response[Body]] = {
    r: IN => SubscriptionHandlerResult[Response[Body]](handler(r), encoder.asInstanceOf[Encoder[Response[Body]]])
  }

}
