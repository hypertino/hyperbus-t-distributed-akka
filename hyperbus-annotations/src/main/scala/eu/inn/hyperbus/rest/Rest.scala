package eu.inn.hyperbus.rest

import java.io.OutputStream

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.serialization.{DecodeException, RequestHeader, MessageEncoder}
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
  def links: Body.LinksMap
}

object Body {
  type LinksMap = Map[String, Either[Link, Seq[Link]]]
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

/*trait StaticRequestObject {
  def method: String
  def contentType: Option[String]

  def checkRequestHeader(requestHeader: RequestHeader): Unit = {
    if (requestHeader.method != method)
      throw new DecodeException(s"Expected method $method but got ${requestHeader.method}")
    if (requestHeader.contentType != contentType)
      throw new DecodeException(s"Expected method $method but got ${requestHeader.method}")
  }
}*/

trait Response[+B <: Body] extends Message[B] with TransportResponse {
  def status: Int
  override def encode(outputStream: java.io.OutputStream) = MessageEncoder.encodeResponse(this, outputStream)
}

// todo: DefinedResponse -> Tupple!

trait DefinedResponse[R <: Response[_]]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]


