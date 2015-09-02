package eu.inn.hyperbus.model

import java.io.OutputStream

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.serialization.MessageSerializer
import eu.inn.hyperbus.transport.api.{TransportMessage, TransportRequest, TransportResponse}

case class Link(href: String, templated: Option[Boolean] = None, @fieldName("type") typ: Option[String] = None)

trait Body {
  def contentType: Option[String]
  def serialize(output: OutputStream)
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
  override def serialize(outputStream: java.io.OutputStream) = MessageSerializer.serializeRequest(this, outputStream)
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
  override def serialize(outputStream: java.io.OutputStream) = MessageSerializer.serializeResponse(this, outputStream)
}

// defines responses:
// * single:                DefinedResponse[Created[TestCreatedBody]]
// * multiple:              DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]
// * alternative multiple:  DefinedResponse[|[Ok[DynamicBody], |[Created[TestCreatedBody], !]]]
trait DefinedResponse[R]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]


