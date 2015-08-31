package eu.inn.hyperbus.transport.inproc

import eu.inn.hyperbus.transport.api._

import scala.concurrent.Future

private[transport] case class Subscription(inputDecoder: Decoder[TransportRequest],
                                           exceptionEncoder: Encoder[Throwable],
                                           handler: (TransportRequest) => Future[TransportResponse])
