package eu.inn.hyperbus.model

import java.io.OutputStream

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.serialization.{StringSerializer, MessageSerializer}
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
  def links: LinksMap.LinksMapType
}

object LinksMap {
  type LinksMapType = Map[String, Either[Link, Seq[Link]]]

  def apply(self: String): LinksMapType = Map(DefLink.SELF → Left(Link(self, templated = Some(true))))

  def apply(link: Link): LinksMapType = Map(DefLink.SELF → Left(link))

  def apply(links: Map[String, Either[Link, Seq[Link]]]): LinksMapType = links
}

trait Message[+B <: Body] extends TransportMessage with MessagingContextFactory {
  def body: B

  def messageId = header(Header.MESSAGE_ID)

  def correlationId = headerOption(Header.CORRELATION_ID).getOrElse(messageId)

  def newContext() = MessagingContext(correlationId)

  override def toString = {
    s"${getClass.getName}[${body.getClass.getName}]:$serializeToString"
  }

  private def serializeToString = {
    val os = new java.io.ByteArrayOutputStream()
    serialize(os)
    os.toString(StringSerializer.defaultEncoding)
  }
}

trait Request[+B <: Body] extends Message[B] with TransportRequest {
  def method: String = header(Header.METHOD)

  protected def assertMethod(value: String): Unit = {
    if (method != value) throw new IllegalArgumentException(s"Incorrect method value: $method != $value (headers?)")
  }

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
