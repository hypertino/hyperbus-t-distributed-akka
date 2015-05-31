package eu.inn.hyperbus.rest.standard

import eu.inn.hyperbus.rest.annotations.method
import eu.inn.hyperbus.rest.{Body, DynamicBody, DynamicRequest, Request}

object Method {
  val GET = "get"
  val POST = "post"
  val PUT = "put"
  val PATCH = "patch"
  val DELETE = "delete"
}

@method("get")
trait Get[+B <: Body] extends Request[B] {
  override def method = Method.GET
}

abstract class StaticGet[+B <: Body](initBody: B) extends Get[B]

case class DynamicGet[+B <: DynamicBody](url: String, body: B) extends Get[B] with DynamicRequest[B]

@method("delete")
trait Delete[+B <: Body] extends Request[B] {
  override def method = Method.DELETE
}

abstract class StaticDelete[+B <: Body](initBody: B) extends Delete[B]

case class DynamicDelete[+B <: DynamicBody](url: String, body: B) extends Delete[B] with DynamicRequest[B]

@method("post")
trait Post[+B <: Body] extends Request[B] {
  override def method = Method.POST
}

abstract class StaticPost[+B <: Body](initBody: B) extends Post[B]

case class DynamicPost[+B <: DynamicBody](url: String, body: B) extends Post[B] with DynamicRequest[B]

@method("put")
trait Put[+B <: Body] extends Request[B] {
  override def method = Method.PUT
}

abstract class StaticPut[+B <: Body](initBody: B) extends Put[B]

case class DynamicPut[+B <: DynamicBody](url: String, body: B) extends Put[B] with DynamicRequest[DynamicBody]

@method("patch")
trait Patch[+B <: Body] extends Request[B] {
  override def method = Method.PATCH
}

abstract class StaticPatch[+B <: Body](initBody: B) extends Patch[B]

case class DynamicPatch[+B <: DynamicBody](url: String, body: B) extends Patch[B] with DynamicRequest[B]
