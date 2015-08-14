package eu.inn.hyperbus.rest.standard

import eu.inn.hyperbus.rest.annotations.method
import eu.inn.hyperbus.rest._

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

case class DynamicGet(
                       url: String,
                       body: DynamicBody,
                       messageId: String = MessagingContext.newMessageId,
                       correlationId: Option[String] = MessagingContext.correlationId
                       ) extends Get[DynamicBody] with DynamicRequest

@method("delete")
trait Delete[+B <: Body] extends Request[B] {
  override def method = Method.DELETE
}

abstract class StaticDelete[+B <: Body](initBody: B) extends Delete[B]

case class DynamicDelete(
                          url: String,
                          body: DynamicBody,
                          messageId: String = MessagingContext.newMessageId,
                          correlationId: Option[String] = MessagingContext.correlationId
                          ) extends Delete[DynamicBody] with DynamicRequest

@method("post")
trait Post[+B <: Body] extends Request[B] {
  override def method = Method.POST
}

abstract class StaticPost[+B <: Body](initBody: B) extends Post[B]

case class DynamicPost(url: String,
                       body: DynamicBody,
                       messageId: String = MessagingContext.newMessageId,
                       correlationId: Option[String] = MessagingContext.correlationId
                        ) extends Post[DynamicBody] with DynamicRequest

@method("put")
trait Put[+B <: Body] extends Request[B] {
  override def method = Method.PUT
}

abstract class StaticPut[+B <: Body](initBody: B) extends Put[B]

case class DynamicPut(
                       url: String,
                       body: DynamicBody,
                       messageId: String = MessagingContext.newMessageId,
                       correlationId: Option[String] = MessagingContext.correlationId
                       ) extends Put[DynamicBody] with DynamicRequest

@method("patch")
trait Patch[+B <: Body] extends Request[B] {
  override def method = Method.PATCH
}

abstract class StaticPatch[+B <: Body](initBody: B) extends Patch[B]

case class DynamicPatch(
                         url: String,
                         body: DynamicBody,
                         messageId: String = MessagingContext.newMessageId,
                         correlationId: Option[String] = MessagingContext.correlationId
                         ) extends Patch[DynamicBody] with DynamicRequest
