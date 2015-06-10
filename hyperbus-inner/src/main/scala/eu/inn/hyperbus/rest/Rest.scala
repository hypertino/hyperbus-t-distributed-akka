package eu.inn.hyperbus.rest

import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.{Null, Value}
import eu.inn.hyperbus.rest.annotations.contentTypeMarker
import eu.inn.hyperbus.rest.standard.ContentType
import eu.inn.hyperbus.utils.ErrorUtils

case class Link(href: String, templated: Option[Boolean] = None, @fieldName("type") typ: Option[String] = None)

trait Body {
  def contentType: Option[String]
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

trait Message[+B <: Body] {
  def body: B
}

trait Request[+B <: Body] extends Message[B] {
  type bodyType = Body

  def url: String

  def method: String
}

trait DynamicRequest extends Request[DynamicBody]

trait Response[+B <: Body] extends Message[B] {
  def status: Int
}

//trait DynamicResponse extends Response[DynamicBody]

trait DynamicBody extends Body with Links {
  def content: Value

  lazy val links: Body.LinksMap = content.__links[Option[Body.LinksMap]] getOrElse Map()
}

object DynamicBody {
  def apply(content: Value, contentType: Option[String] = None): DynamicBody = DynamicBodyContainer(content, contentType)
}

case class DynamicBodyContainer(content: Value, contentType: Option[String] = None) extends DynamicBody

@contentTypeMarker("no-content")
trait EmptyBody extends Body

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = Some(ContentType.NO_CONTENT)
}

// --------------- Response Groups ---------------

trait NormalResponse extends Response[Body]

trait RedirectResponse extends Response[Body]

trait ErrorBodyApi extends Body {
  def code: String

  def message: String

  def errorId: String

  def description: Option[String]
}

trait ErrorResponse extends Response[ErrorBodyApi]

trait ServerError extends ErrorResponse

trait ClientError extends ErrorResponse

case class ErrorBody(code: String,
                     description: Option[String] = None,
                     errorId: String = ErrorUtils.createErrorId,
                     extra: Value = Null) extends ErrorBodyApi with NoContentType {
  def message = code + description.map(": " + _).getOrElse("") + ". #" + errorId
}

trait DefinedResponse[R <: Response[_]]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]

