package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._

import scala.concurrent.Future

private[transport] case class HandlerWrapper(inputDeserializer: RequestDeserializer[Request[Body]],
                                             handler: Request[Body] => Future[TransportResponse] =
                                                (_ ⇒ throw new RuntimeException("inproc request handler is not specified")))
