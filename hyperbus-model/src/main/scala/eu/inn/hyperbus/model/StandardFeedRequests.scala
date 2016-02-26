package eu.inn.hyperbus.model

/*

trait FeedRequest[+B <: Body] extends Request[B]

@method("feed:get")
trait FeedGet[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_GET
}

abstract class StaticFeedGet[+B <: Body](initBody: B) extends FeedGet[B]

case class DynamicFeedGet(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]]) extends FeedGet[DynamicBody] with DynamicRequest

object DynamicFeedGet {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedGet = {
    DynamicFeedGet(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedGet = apply(uri, body, new HeadersBuilder)(contextFactory)
}


@method("feed:post")
trait FeedPost[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_POST
}

abstract class StaticFeedPost[+B <: Body](initBody: B) extends FeedPost[B]

case class DynamicFeedPost(uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]]) extends FeedPost[DynamicBody] with DynamicRequest

object FeedDynamicPost {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPost = {
    DynamicFeedPost(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPost = apply(uri, body, new HeadersBuilder)(contextFactory)
}

@method("feed:put")
trait FeedPut[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_PUT
}

abstract class StaticFeedPut[+B <: Body](initBody: B) extends FeedPut[B]

case class DynamicFeedPut(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]]) extends FeedPut[DynamicBody] with DynamicRequest

object DynamicFeedPut {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPut = {
    DynamicFeedPut(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPut = apply(uri, body, new HeadersBuilder)(contextFactory)
}

@method("feed:patch")
trait FeedPatch[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_PATCH
}

abstract class StaticFeedPatch[+B <: Body](initBody: B) extends FeedPatch[B]

case class DynamicFeedPatch(
                         uri: Uri,
                         body: DynamicBody,
                         headers: Map[String, Seq[String]]) extends FeedPatch[DynamicBody] with DynamicRequest

object DynamicFeedPatch {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPatch = {
    DynamicFeedPatch(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedPatch = apply(uri, body, new HeadersBuilder)(contextFactory)
}

@method("feed:delete")
trait FeedDelete[+B <: Body] extends FeedRequest[B] {
  override def method = Method.FEED_DELETE
}

abstract class StaticFeedDelete[+B <: Body](initBody: B) extends FeedDelete[B]

case class DynamicFeedDelete(
                          uri: Uri,
                          body: DynamicBody,
                          headers: Map[String, Seq[String]]) extends FeedDelete[DynamicBody] with DynamicRequest

object DynamicFeedDelete {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedDelete = {
    DynamicFeedDelete(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicFeedDelete = apply(uri, body, new HeadersBuilder)(contextFactory)
}

*/