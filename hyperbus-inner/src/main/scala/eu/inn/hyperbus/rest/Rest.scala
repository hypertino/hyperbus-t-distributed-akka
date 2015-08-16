package eu.inn.hyperbus.rest

import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.{Null, Value}
import eu.inn.hyperbus.rest.annotations.contentTypeMarker
import eu.inn.hyperbus.rest.standard.ContentType
import eu.inn.hyperbus.utils.IdUtils

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
  def messageId: String
  def correlationId: Option[String] //todo: check spelling
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

  lazy val links: Body.LinksMap = content.__links[Option[Body.LinksMap]] getOrElse Map.empty
}

object DynamicBody {
  def apply(content: Value, contentType: Option[String] = None): DynamicBody = DynamicBodyContainer(content, contentType)
  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.content, dynamicBody.contentType))
}

private [rest] case class DynamicBodyContainer(content: Value, contentType: Option[String] = None) extends DynamicBody

@contentTypeMarker("no-content")
trait EmptyBody extends Body

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = Some(ContentType.NO_CONTENT)
}

// --------------- Response Groups ---------------

trait NormalResponse extends Response[Body]

trait RedirectResponse extends Response[Body]

trait ErrorBody extends Body {
  def code: String

  def description: Option[String]

  def errorId: String

  def extra: Value

  def message: String
}

trait ErrorResponse extends Response[ErrorBody]

trait ServerError extends ErrorResponse

trait ClientError extends ErrorResponse

object ErrorBody {
  def apply(code: String,
            description: Option[String] = None,
            errorId: String = IdUtils.createId,
            extra: Value = Null,
            contentType: Option[String] = None): ErrorBody =
    ErrorBodyContainer(code, description, errorId, extra, contentType)

  def unapply(errorBody: ErrorBody) = Some(
    (errorBody.code, errorBody.description, errorBody.errorId, errorBody.extra, errorBody.contentType)
  )
}

private [rest] case class ErrorBodyContainer(code: String,
                                             description: Option[String],
                                             errorId: String,
                                             extra: Value,
                                             contentType: Option[String]) extends ErrorBody {
  def message = code + description.map(": " + _).getOrElse("") + ". #" + errorId
}

trait DefinedResponse[R <: Response[_]]

trait |[L <: Response[Body], R <: Response[Body]] extends Response[Body]

trait ! extends Response[Body]

trait MessagingContext {
  def correlationId: Option[String]
}

object MessagingContext {
  implicit val defaultMessagingContext = new MessagingContext {
    override def correlationId: Option[String] = None
  }

  def findContext(implicit context: MessagingContext): MessagingContext = context
  def correlationId(implicit context: MessagingContext): Option[String] = findContext(context).correlationId
}