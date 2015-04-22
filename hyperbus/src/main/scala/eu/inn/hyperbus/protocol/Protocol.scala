package eu.inn.hyperbus.protocol

import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.DynamicValue

object Status {
  val OK = 200
  val CREATED = 201
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

// todo: is this needed?
object StandardErrors {
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
  def url: String
  def method: String
}

trait Response[+B <: Body] extends Message[B] {
  def status: Int
}

//success, redirect, etc

// --------------- Responses ---------------

trait NormalResponse[+B <: Body] extends Response[B]

case class OK[+B <: Body](body: B) extends Response[B] with NormalResponse[B] {
  override def status: Int = Status.OK
}

trait CreatedBody extends Body with Links {
  def location = links(StandardLink.LOCATION)
}

case class Created[+B <: CreatedBody](body: B) extends Response[B] with NormalResponse[B] {
  override def status: Int = Status.CREATED
}

trait ErrorResponse[+B <: Body] extends Response[B]

case class InternalError[+B <: Body](body: B) extends Response[B] with ErrorResponse[B] {
  override def status: Int = Status.INTERNAL_ERROR
}

case class ErrorBody(error:String,
                     description:Option[String] = None,
                     errorId: Option[String] = None,// todo: common mechanism
                     contentType: Option[String] = None) extends Body

/*
class CreatedResponseBodyStatic(initLocation: Link, otherLinks: Map[String,Link] = Map()) extends  CreatedResponseBody {
  override def links = otherLinks + (StandardLink.LOCATION -> initLocation)
}
*/

// --------------- Request classes ---------------
trait DynamicRequest

trait Get[+B <: Body] extends Request[B] {
  override def method = StandardMethods.GET
}
abstract class StaticGet[+B <: Body](initBody: B) extends Get[B]
case class DynamicGet[+B <: Body](url: String, body: B) extends Get[B] with DynamicRequest

trait Delete[+B <: Body] extends Request[B] {
  override def method = StandardMethods.DELETE
}
abstract class StaticDelete[+B <: Body](initBody: B) extends Delete[B]
case class DynamicDelete[+B <: Body](url: String, body: B) extends Delete[B] with DynamicRequest

trait Post[+B <: Body] extends Request[B] {
  override def method = StandardMethods.POST
}
abstract class StaticPost[+B <: Body](initBody: B) extends Post[B]
case class DynamicPost[+B <: Body](url: String, body: B) extends Post[B] with DynamicRequest

trait Put[+B <: Body] extends Request[B] {
  override def method = StandardMethods.PUT
}
abstract class StaticPut[+B <: Body](initBody: B) extends Put[B]
case class DynamicPut[+B <: Body](url: String, body: B) extends Put[B] with DynamicRequest

trait Patch[+B <: Body] extends Request[B] {
  override def method = StandardMethods.PATCH
}
abstract class StaticPatch[+B <: Body](initBody: B) extends Patch[B]
case class DynamicPatch[+B <: Body](url: String, body: B) extends Patch[B] with DynamicRequest

trait DefinedResponse[R <: Response[_]]
trait |[L<: Response[Body], R <: Response[Body]] extends Response[Body]
trait ! extends Response[Body]

// --------------- Dynamic ---------------

case class DynamicBody(content: DynamicValue, contentType: Option[String] = None) extends Body with Links{
  val links: Body.LinksMap = content.__links[Option[Body.LinksMap]] getOrElse Map()
}