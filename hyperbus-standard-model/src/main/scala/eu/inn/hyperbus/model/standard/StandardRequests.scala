package eu.inn.hyperbus.model.standard

import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.method
import eu.inn.hyperbus.transport.api.uri.Uri

@method("get")
trait Get[+B <: Body] extends Request[B] {
  override def method = Method.GET
}

abstract class StaticGet[+B <: Body](initBody: B) extends Get[B]

case class DynamicGet(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]],
                       messageId: String,
                       correlationId: String) extends Get[DynamicBody] with DynamicRequest

object DynamicGet {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicGet = {
    val ctx = contextFactory.newContext()
    DynamicGet(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicGet = apply(uri, body, Map.empty)(contextFactory)
}


@method("post")
trait Post[+B <: Body] extends Request[B] {
  override def method = Method.POST
}

abstract class StaticPost[+B <: Body](initBody: B) extends Post[B]

case class DynamicPost(uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]],
                       messageId: String,
                       correlationId: String) extends Post[DynamicBody] with DynamicRequest

object DynamicPost {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicPost = {
    val ctx = contextFactory.newContext()
    DynamicPost(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicPost = apply(uri, body, Map.empty)(contextFactory)
}

@method("put")
trait Put[+B <: Body] extends Request[B] {
  override def method = Method.PUT
}

abstract class StaticPut[+B <: Body](initBody: B) extends Put[B]

case class DynamicPut(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]],
                       messageId: String,
                       correlationId: String) extends Put[DynamicBody] with DynamicRequest

object DynamicPut {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicPut = {
    val ctx = contextFactory.newContext()
    DynamicPut(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicPut = apply(uri, body, Map.empty)(contextFactory)
}

@method("patch")
trait Patch[+B <: Body] extends Request[B] {
  override def method = Method.PATCH
}

abstract class StaticPatch[+B <: Body](initBody: B) extends Patch[B]

case class DynamicPatch(
                         uri: Uri,
                         body: DynamicBody,
                         headers: Map[String, Seq[String]],
                         messageId: String,
                         correlationId: String) extends Patch[DynamicBody] with DynamicRequest

object DynamicPatch {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicPatch = {
    val ctx = contextFactory.newContext()
    DynamicPatch(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicPatch = apply(uri, body, Map.empty)(contextFactory)
}

@method("delete")
trait Delete[+B <: Body] extends Request[B] {
  override def method = Method.DELETE
}

abstract class StaticDelete[+B <: Body](initBody: B) extends Delete[B]

case class DynamicDelete(
                          uri: Uri,
                          body: DynamicBody,
                          headers: Map[String, Seq[String]],
                          messageId: String,
                          correlationId: String) extends Delete[DynamicBody] with DynamicRequest

object DynamicDelete {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicDelete = {
    val ctx = contextFactory.newContext()
    DynamicDelete(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicDelete = apply(uri, body, Map.empty)(contextFactory)
}

