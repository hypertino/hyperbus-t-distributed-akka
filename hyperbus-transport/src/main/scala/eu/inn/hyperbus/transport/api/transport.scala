package eu.inn.hyperbus.transport.api

import java.io.OutputStream

import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait EntityWithHeaders {
  def headers: Map[String, Seq[String]]

  def headerOption(name: String): Option[String] = headers.get(name).flatMap(_.headOption)

  def header(name: String): String = headerOption(name).getOrElse(throw new NoSuchHeaderException(name))
}

// todo: toString with JSON and class info
trait TransportMessage extends EntityWithHeaders {
  def messageId: String

  def correlationId: String

  def serialize(output: OutputStream)
}

trait TransportRequest extends TransportMessage {
  def uri: Uri
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
  def onCommand[IN <: TransportRequest](matcher: TransportRequestMatcher,
                                        inputDeserializer: Deserializer[IN])
                                       (handler: (IN) => Future[TransportResponse]): String

  def onEvent[IN <: TransportRequest](matcher: TransportRequestMatcher,
                                      groupName: String,
                                      inputDeserializer: Deserializer[IN])
                                     (handler: (IN) => Future[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)
class NoSuchHeaderException(header: String) extends RuntimeException(s"No such header: $header")