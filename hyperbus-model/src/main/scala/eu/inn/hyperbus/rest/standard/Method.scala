package eu.inn.hyperbus.rest.standard

import eu.inn.hyperbus.rest.annotations.method
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.utils.IdUtils

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
                       messageId: String,
                       correlationId: String) extends Get[DynamicBody] with DynamicRequest

object DynamicGet {
  def apply(url: String, body: DynamicBody)(implicit contextFactory: MessagingContextFactory): DynamicGet = {
    val ctx = contextFactory.newContext()
    DynamicGet(url,body,ctx.messageId,ctx.correlationId)
  }
}

@method("delete")
trait Delete[+B <: Body] extends Request[B] {
  override def method = Method.DELETE
}

abstract class StaticDelete[+B <: Body](initBody: B) extends Delete[B]

case class DynamicDelete(
                          url: String,
                          body: DynamicBody,
                          messageId: String,
                          correlationId: String) extends Delete[DynamicBody] with DynamicRequest

object DynamicDelete {
  def apply(url: String, body: DynamicBody)(implicit contextFactory: MessagingContextFactory): DynamicDelete = {
    val ctx = contextFactory.newContext()
    DynamicDelete(url,body,ctx.messageId,ctx.correlationId)
  }
}

@method("post")
trait Post[+B <: Body] extends Request[B] {
  override def method = Method.POST
}

abstract class StaticPost[+B <: Body](initBody: B) extends Post[B]

case class DynamicPost(url: String,
                       body: DynamicBody,
                       messageId: String,
                       correlationId: String) extends Post[DynamicBody] with DynamicRequest

object DynamicPost {
  def apply(url: String, body: DynamicBody)(implicit contextFactory: MessagingContextFactory): DynamicPost = {
    val ctx = contextFactory.newContext()
    DynamicPost(url,body,ctx.messageId,ctx.correlationId)
  }
}

@method("put")
trait Put[+B <: Body] extends Request[B] {
  override def method = Method.PUT
}

abstract class StaticPut[+B <: Body](initBody: B) extends Put[B]

case class DynamicPut(
                       url: String,
                       body: DynamicBody,
                       messageId: String,
                       correlationId: String) extends Put[DynamicBody] with DynamicRequest

object DynamicPut {
  def apply(url: String, body: DynamicBody)(implicit contextFactory: MessagingContextFactory): DynamicPut = {
    val ctx = contextFactory.newContext()
    DynamicPut(url,body,ctx.messageId,ctx.correlationId)
  }
}

@method("patch")
trait Patch[+B <: Body] extends Request[B] {
  override def method = Method.PATCH
}

abstract class StaticPatch[+B <: Body](initBody: B) extends Patch[B]

case class DynamicPatch(
                         url: String,
                         body: DynamicBody,
                         messageId: String,
                         correlationId: String) extends Patch[DynamicBody] with DynamicRequest

object DynamicPatch {
  def apply(url: String, body: DynamicBody)(implicit contextFactory: MessagingContextFactory): DynamicPatch = {
    val ctx = contextFactory.newContext()
    DynamicPatch(url,body,ctx.messageId,ctx.correlationId)
  }
}
