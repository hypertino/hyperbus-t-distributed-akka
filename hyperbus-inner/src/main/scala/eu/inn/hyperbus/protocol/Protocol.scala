package eu.inn.hyperbus.protocol

import java.util.UUID

import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.{Null, Number, Obj, Value}
import eu.inn.hyperbus.protocol.annotations.method

object Status {
  val OK = 200
  val CREATED = 201

  val NOT_FOUND=404
  val CONFLICT=409

  val INTERNAL_ERROR=500
}

object StandardLink {
  val SELF = "self"
  val LOCATION = "location"
}

object StandardMethods {
  val GET = "get"
  val POST = "post"
  val PUT = "put"
  val PATCH = "patch"
  val DELETE = "delete"
}

object StandardErrors {
  val INTERNAL_ERROR = "internal_error"
  val HANDLER_NOT_FOUND = "handler_not_found"
}

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
  type LinksMap = Map[String, Either[Link,Seq[Link]]]
}

trait Message[+B <: Body]{
  def body: B
}

trait Request[+B <: Body] extends Message[B]{
  type bodyType = Body
  def url: String
  def method: String
}

trait Response[+B <: Body] extends Message[B] {
  def status: Int
}

//success, redirect, etc

// --------------- Responses ---------------

trait NormalResponse[+B <: Body] extends Response[B]

case class Ok[+B <: Body](body: B) extends Response[B] with NormalResponse[B] {
  override def status: Int = Status.OK
}

trait CreatedBody extends Body with Links {
  def location = links(StandardLink.LOCATION)
}

case class Created[+B <: CreatedBody](body: B) extends Response[B] with NormalResponse[B] {
  override def status: Int = Status.CREATED
}

trait ErrorBodyTrait extends Body {
  def code: String
  def message: String
  def errorId: String
  def description: Option[String]
}

trait ErrorResponse[+B <: ErrorBodyTrait] extends Response[B]

trait ServerError[+B <: ErrorBodyTrait] extends ErrorResponse[B]

trait ClientError[+B <: ErrorBodyTrait] extends ErrorResponse[B]

case class InternalError[+B <: ErrorBodyTrait](body: B)
  extends RuntimeException(body.message) with Response[B] with ServerError[B] {
  override def status: Int = Status.INTERNAL_ERROR
}

case class NotFound[+B <: ErrorBodyTrait](body: B)
  extends RuntimeException(body.message) with Response[B] with ClientError[B] {
  override def status: Int = Status.NOT_FOUND
}

case class Conflict[+B <: ErrorBodyTrait](body: B)
  extends RuntimeException(body.message) with Response[B] with ClientError[B] {
  override def status: Int = Status.CONFLICT
}

case class ErrorBody(code:String,
                     description:Option[String] = None,
                     errorId: String = UUID.randomUUID().toString,
                     extra: Value = Null) extends ErrorBodyTrait with NoContentType {
  def message = code + description.map(": " + _).getOrElse("")
}

/*
class CreatedResponseBodyStatic(initLocation: Link, otherLinks: Map[String,Link] = Map()) extends  CreatedResponseBody {
  override def links = otherLinks + (StandardLink.LOCATION -> initLocation)
}
*/

// --------------- Request classes ---------------
trait DynamicRequest

@method("get")
trait Get[+B <: Body] extends Request[B] {
  override def method = StandardMethods.GET
}
abstract class StaticGet[+B <: Body](initBody: B) extends Get[B]
case class DynamicGet[+B <: Body](url: String, body: B) extends Get[B] with DynamicRequest

@method("delete")
trait Delete[+B <: Body] extends Request[B] {
  override def method = StandardMethods.DELETE
}
abstract class StaticDelete[+B <: Body](initBody: B) extends Delete[B]
case class DynamicDelete[+B <: Body](url: String, body: B) extends Delete[B] with DynamicRequest

@method("post")
trait Post[+B <: Body] extends Request[B] {
  override def method = StandardMethods.POST
}
abstract class StaticPost[+B <: Body](initBody: B) extends Post[B]
case class DynamicPost[+B <: Body](url: String, body: B) extends Post[B] with DynamicRequest

@method("put")
trait Put[+B <: Body] extends Request[B] {
  override def method = StandardMethods.PUT
}
abstract class StaticPut[+B <: Body](initBody: B) extends Put[B]
case class DynamicPut[+B <: Body](url: String, body: B) extends Put[B] with DynamicRequest

@method("patch")
trait Patch[+B <: Body] extends Request[B] {
  override def method = StandardMethods.PATCH
}
abstract class StaticPatch[+B <: Body](initBody: B) extends Patch[B]
case class DynamicPatch[+B <: Body](url: String, body: B) extends Patch[B] with DynamicRequest

trait DefinedResponse[R <: Response[_]]
trait |[L<: Response[Body], R <: Response[Body]] extends Response[Body]
trait ! extends Response[Body]

// --------------- Dynamic ---------------

trait DynamicBody extends Body with Links{
  def content: Value
  lazy val links: Body.LinksMap = content.__links[Option[Body.LinksMap]] getOrElse Map()
}

// todo: too long name
case class DefaultDynamicBody(content: Value, contentType: Option[String] = None) extends DynamicBody

case class CreatedDynamicBody(content: Value, contentType: Option[String] = None) extends DynamicBody with CreatedBody

case class EmptyBody(contentType: Option[String] = None) extends Body
