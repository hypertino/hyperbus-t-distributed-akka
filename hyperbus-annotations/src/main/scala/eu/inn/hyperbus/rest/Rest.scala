package eu.inn.hyperbus.rest

import java.io.OutputStream

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.serialization.MessageEncoder
import eu.inn.servicebus.transport._

case class Link(href: String, templated: Option[Boolean] = None, @fieldName("type") typ: Option[String] = None)

trait Body {
  def contentType: Option[String]
  def encode(output: OutputStream)
}

trait NoContentType {
  def contentType: Option[String] = None
}

trait Links {
  def links: Links.Map
}

object Links {
  type Map = scala.collection.Map[String, Either[Link, Seq[Link]]]
}

trait Message[+B <: Body] extends TransportMessage with MessagingContextFactory {
  outer ⇒
  def body: B
  def messageId: String
  def correlationId: String

  def newContext() = MessagingContext(correlationId)
}

trait Request[+B <: Body] extends Message[B] with TransportRequest {
  type bodyType = Body
  def url: String
  def method: String
  override def encode(outputStream: java.io.OutputStream) = MessageEncoder.encodeRequest(this, outputStream)
}

trait Response[+B <: Body] extends Message[B] with TransportResponse {
  def status: Int
  override def encode(outputStream: java.io.OutputStream) = MessageEncoder.encodeResponse(this, outputStream)
}

// todo: DefinedResponse -> Tupple!

trait DefinedResponse[R <: Response[_]]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]


