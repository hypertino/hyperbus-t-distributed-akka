package eu.inn.hyperbus.transport.api

import java.io.OutputStream

import com.typesafe.config.ConfigValue

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

trait TransportMessage {
  def messageId: String

  def correlationId: String

  def serialize(output: OutputStream)
}

trait TransportRequest extends TransportMessage {
  def topic: Topic
}

trait TransportResponse extends TransportMessage

trait PublishResult {
  def sent: Option[Boolean]

  def offset: Option[String]
}

trait ClientTransport {
  def ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT]): Future[OUT]

  def publish(message: TransportRequest): Future[PublishResult]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

trait ServerTransport {
  def process[IN <: TransportRequest](topicFilter: Topic, inputDeserializer: Deserializer[IN], exceptionSerializer: Serializer[Throwable])
                                     (handler: (IN) => Future[TransportResponse]): String

  def subscribe[IN <: TransportRequest](topicFilter: Topic, groupName: String, inputDeserializer: Deserializer[IN])
                                       (handler: (IN) => Future[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)
