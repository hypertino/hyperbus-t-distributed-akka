package eu.inn.servicebus.transport

import eu.inn.servicebus.serialization.{Decoder, Encoder}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class KafkaClientTransport extends ClientTransport {
  override def ask[OUT, IN](topic: Topic, message: IN, inputEncoder: Encoder[IN], outputDecoder: Decoder[OUT]): Future[OUT] = ???

  override def publish[IN](topic: Topic, message: IN, inputEncoder: Encoder[IN]): Future[Unit] = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = ???
}
