package eu.inn.hyperbus.model

import java.io.OutputStream

import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.serialization.{MessageSerializer, StringSerializer}
import eu.inn.hyperbus.transport.api.{TransportMessage, TransportRequest, TransportResponse}

import scala.collection.mutable

case class Link(href: String, templated: Boolean = false, @fieldName("type") typ: Option[String] = None)

trait Body {
  def contentType: Option[String]

  def serialize(output: OutputStream)
}

trait NoContentType {
  def contentType: Option[String] = None
}

trait Links {
  def links: Links.LinksMap
}

object Links {
  type LinksMap = Map[String, Either[Link, Seq[Link]]]

  def apply(selfHref: String, templated: Boolean = false, typ: Option[String] = None): LinksMap = {
    new LinksBuilder() self(selfHref, templated, typ) result()
  }

  def location(locationHref: String, templated: Boolean = false, typ: Option[String] = None): LinksMap = {
    new LinksBuilder() location (locationHref, templated, typ) result()
  }

  def apply(key: String, link: Link): LinksMap = new LinksBuilder() add(key, link) result()
}

class LinksBuilder(private [this] val args: mutable.Map[String, Either[Link, Seq[Link]]]) {
  def this() = this(mutable.Map[String, Either[Link, Seq[Link]]]())

  def self(selfHref: String, templated: Boolean = true, typ: Option[String] = None) = {
    args += DefLink.SELF → Left(Link(selfHref, templated))
    this
  }
  def location(locationHref: String, templated: Boolean = true, typ: Option[String] = None) = {
    args += DefLink.LOCATION → Left(Link(locationHref, templated))
    this
  }
  def add(key: String, href: String, templated: Boolean = true, typ: Option[String] = None): LinksBuilder = {
    add(key, Link(href, templated, typ))
    this
  }
  def add(key: String, link : Link) = {
    args.get(key) match {
      case Some(Left(existingLink)) ⇒
        args += key → Right(Seq(existingLink, link))

      case Some(Right(existingLinks)) ⇒
        args += key → Right(existingLinks :+ link)

      case None ⇒
        args += key → Left(link)
    }
    this
  }
  def add(links: Seq[(String, Either[Link, Seq[Link]])]): LinksBuilder = {
    if (args.isEmpty) {
      args ++= links
    }
    else {
      links.foreach {
        case (k, Left(v)) ⇒ add(k, v)
        case (k, Right(v)) ⇒ v.foreach(vi ⇒ add(k, vi))
      }
    }
    this
  }
  def result(): Links.LinksMap = args.toMap
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
