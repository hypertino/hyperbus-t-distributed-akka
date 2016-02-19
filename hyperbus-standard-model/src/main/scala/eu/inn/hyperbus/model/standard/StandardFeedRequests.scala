package eu.inn.hyperbus.model.standard

import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.method
import eu.inn.hyperbus.transport.api.uri.Uri

trait FeedRequest[+B <: Body] extends Request[B]

@method("feed:get")
trait FeedGet[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_GET
}

abstract class StaticFeedGet[+B <: Body](initBody: B) extends FeedGet[B]

case class DynamicFeedGet(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]],
                       messageId: String,
                       correlationId: String) extends FeedGet[DynamicBody] with DynamicRequest

object DynamicFeedGet {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicFeedGet = {
    val ctx = contextFactory.newContext()
    DynamicFeedGet(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedGet = apply(uri, body, Map.empty)(contextFactory)
}


@method("feed:post")
trait FeedPost[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_POST
}

abstract class StaticFeedPost[+B <: Body](initBody: B) extends FeedPost[B]

case class DynamicFeedPost(uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]],
                       messageId: String,
                       correlationId: String) extends FeedPost[DynamicBody] with DynamicRequest

object FeedDynamicPost {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPost = {
    val ctx = contextFactory.newContext()
    DynamicFeedPost(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPost = apply(uri, body, Map.empty)(contextFactory)
}

@method("feed:put")
trait FeedPut[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_PUT
}

abstract class StaticFeedPut[+B <: Body](initBody: B) extends FeedPut[B]

case class DynamicFeedPut(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]],
                       messageId: String,
                       correlationId: String) extends FeedPut[DynamicBody] with DynamicRequest

object DynamicFeedPut {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPut = {
    val ctx = contextFactory.newContext()
    DynamicFeedPut(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPut = apply(uri, body, Map.empty)(contextFactory)
}

@method("feed:patch")
trait FeedPatch[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_PATCH
}

abstract class StaticFeedPatch[+B <: Body](initBody: B) extends FeedPatch[B]

case class DynamicFeedPatch(
                         uri: Uri,
                         body: DynamicBody,
                         headers: Map[String, Seq[String]],
                         messageId: String,
                         correlationId: String) extends FeedPatch[DynamicBody] with DynamicRequest

object DynamicFeedPatch {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPatch = {
    val ctx = contextFactory.newContext()
    DynamicFeedPatch(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPatch = apply(uri, body, Map.empty)(contextFactory)
}

@method("feed:delete")
trait FeedDelete[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_DELETE
}

abstract class StaticFeedDelete[+B <: Body](initBody: B) extends FeedDelete[B]

case class DynamicFeedDelete(
                          uri: Uri,
                          body: DynamicBody,
                          headers: Map[String, Seq[String]],
                          messageId: String,
                          correlationId: String) extends FeedDelete[DynamicBody] with DynamicRequest

object DynamicFeedDelete {
  def apply(uri: Uri, body: DynamicBody, headers: Map[String, Seq[String]])
           (implicit contextFactory: MessagingContextFactory): DynamicFeedDelete = {
    val ctx = contextFactory.newContext()
    DynamicFeedDelete(uri, body, headers, ctx.messageId, ctx.correlationId)
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedDelete = apply(uri, body, Map.empty)(contextFactory)
}

