package eu.inn.hyperbus.protocol

import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.DynamicValue

object Status {
  val OK = 200
  val CREATED = 201
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

abstract class Message[B <: Body](initBody:B){
  def body: B = initBody
}

abstract class Request[B <: Body](initBody:B) extends Message[B](initBody){
  def url: String
  def method: String
}

abstract class Response[B <: Body](initBody:B) extends Message[B](initBody) {
  def status: Int
}

//success, redirect, etc

// --------------- Responses ---------------

class OK[B <: Body](initBody: B) extends Response[B](initBody) {
  override def status: Int = Status.OK
}

trait CreatedBody extends Body with Links {
  def location = links(StandardLink.LOCATION)
}

class Created[B <: CreatedBody](initBody: B) extends Response[B](initBody) {
  override def status: Int = Status.CREATED
}

/*
class CreatedResponseBodyStatic(initLocation: Link, otherLinks: Map[String,Link] = Map()) extends  CreatedResponseBody {
  override def links = otherLinks + (StandardLink.LOCATION -> initLocation)
}
*/

// --------------- Request classes ---------------

abstract class Get[B <: Body](initBody: B) extends Request[B](initBody) {
  override def method = StandardMethods.GET
}

abstract class Delete[B <: Body](initBody: B) extends Request[B](initBody) {
  override def method = StandardMethods.DELETE
}

abstract class Post[B <: Body](initBody: B) extends Request[B](initBody) {
  override def method = StandardMethods.POST
}

abstract class Put[B <: Body](initBody: B) extends Request[B](initBody) {
  override def method = StandardMethods.PUT
}

abstract class Patch[B <: Body](initBody: B) extends Request[B](initBody) {
  override def method = StandardMethods.PATCH
}

trait DefinedResponse[R <: Response[_]] {
  type responseType = R
}

trait DynamicRequest

case class DynamicGet[B <: Body](url: String, initBody: B) extends Get(initBody) with DynamicRequest
class DynamicDelete[B <: Body](initBody: B, val url: String) extends Get(initBody) with DynamicRequest
class DynamicPost[B <: Body](initBody: B, val url: String) extends Get(initBody) with DynamicRequest
class DynamicPut[B <: Body](initBody: B, val url: String) extends Get(initBody) with DynamicRequest
class DynamicPatch[B <: Body](initBody: B, val url: String) extends Get(initBody) with DynamicRequest

// --------------- Dynamic ---------------

case class DynamicBody(content: DynamicValue, contentType: Option[String]) extends Body with Links{
  val links: Body.LinksMap = content.__links[Body.LinksMap]
}