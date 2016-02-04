package eu.inn.hyperbus.model

import java.io.{ByteArrayOutputStream, OutputStream}

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.serialization.MessageSerializer
import eu.inn.hyperbus.transport.api.{TransportMessage, TransportRequest, TransportResponse}

case class Link(href: String, templated: Option[Boolean] = None, @fieldName("type") typ: Option[String] = None)

trait Body {
  def contentType: Option[String]

  def serialize(output: OutputStream)

  def serializeToString(encoding: String = "UTF-8"): String = {
    val outputStream = new ByteArrayOutputStream()
    serialize(outputStream)
    outputStream.toString(encoding)
  }
}

trait NoContentType {
  def contentType: Option[String] = None
}

trait Links {
  def links: LinksMap.LinksMapType
}

object LinksMap {
  type LinksMapType = Map[String, Either[Link, Seq[Link]]]
  def apply(self: String): LinksMapType = Map("self" → Left(Link("/test-inner-resource", templated = Some(true))))
  def apply(link: Link): LinksMapType = Map("self" → Left(link))
  def apply(links: Map[String, Either[Link, Seq[Link]]]): LinksMapType = links
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

  def method: String

  override def serialize(outputStream: java.io.OutputStream) = MessageSerializer.serializeRequest(this, outputStream)
}

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
