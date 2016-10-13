package eu.inn.hyperbus.transport.api

import java.io.OutputStream

import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization.RequestDeserializer
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import rx.lang.scala.Subscriber

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait EntityWithHeaders {
  def headers: Map[String, Seq[String]]

  def headerOption(name: String): Option[String] = headers.get(name).flatMap(_.headOption)

  def header(name: String): String = headerOption(name).getOrElse(throw new NoSuchHeaderException(name))
}

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
  def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse]

  def publish(message: TransportRequest): Future[PublishResult]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

trait Subscription

trait ServerTransport {
  def onCommand(matcher: RequestMatcher,
                inputDeserializer: RequestDeserializer[Request[Body]])
               (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription]

  def onEvent[REQ <: Request[Body]](matcher: RequestMatcher,
              groupName: String,
              inputDeserializer: RequestDeserializer[REQ],
              subscriber: Subscriber[REQ]): Future[Subscription]

  def off(subscription: Subscription): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)

class NoSuchHeaderException(header: String) extends RuntimeException(s"No such header: $header")