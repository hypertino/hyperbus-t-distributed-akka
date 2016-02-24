package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api._

import scala.concurrent.Future

private[transport] case class HandlerWrapper(inputDeserializer: Deserializer[TransportRequest],
                                             handler: (TransportRequest) => Future[TransportResponse])
