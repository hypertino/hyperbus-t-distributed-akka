package eu.inn.servicebus.transport.inproc

import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{TransportResponse, TransportRequest}

import scala.concurrent.Future

private[transport] case class Subscription(inputDecoder: Decoder[TransportRequest],
                                           exceptionEncoder: Encoder[Throwable],
                                           handler: (TransportRequest) => Future[TransportResponse])
