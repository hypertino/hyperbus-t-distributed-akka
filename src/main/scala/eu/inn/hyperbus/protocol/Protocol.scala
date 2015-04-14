package eu.inn.hyperbus.protocol

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

case class Link(href: String, templated: Option[Boolean] = None, typ: Option[String] = None)

trait Body

trait Links {
  def links: Map[String, Link]
}

trait ContentType {
  def contentType: String
}

abstract class Message[B <: Body](initBody:B){
  def body: B = initBody
}

abstract class Request[B <: Body](initBody:B) extends Message[B](initBody){
  def uri: String
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

class CreatedResponseBody(initLocation: Link, otherLinks: Map[String, Link] = Map())
  extends Body with Links {
  val links = otherLinks + (StandardLink.LOCATION -> initLocation)
  def location = links(StandardLink.LOCATION)
}

class Created[B <: CreatedResponseBody](initBody: B) extends Response[B](initBody) {
  override def status: Int = Status.CREATED
}

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

// --------------- Dynamic ---------------

class DynamicBody(content: DynamicValue) extends Body