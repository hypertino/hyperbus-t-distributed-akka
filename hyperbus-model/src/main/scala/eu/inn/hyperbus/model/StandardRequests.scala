package eu.inn.hyperbus.model

/*
@method("get")
trait Get[+B <: Body] extends Request[B] {
  override def method = Method.GET
}

abstract class StaticGet[+B <: Body](initBody: B) extends Get[B]

case class DynamicGet(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]]) extends Get[DynamicBody] with DynamicRequest

object DynamicGet {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicGet = {
    DynamicGet(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicGet = apply(uri, body, new HeadersBuilder)(contextFactory)
}


@method("post")
trait Post[+B <: Body] extends Request[B] {
  override def method = Method.POST
}

abstract class StaticPost[+B <: Body](initBody: B) extends Post[B]

case class DynamicPost(uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]]) extends Post[DynamicBody] with DynamicRequest

object DynamicPost {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicPost = {
    DynamicPost(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicPost = apply(uri, body, new HeadersBuilder)(contextFactory)
}

@method("put")
trait Put[+B <: Body] extends Request[B] {
  override def method = Method.PUT
}

abstract class StaticPut[+B <: Body](initBody: B) extends Put[B]

case class DynamicPut(
                       uri: Uri,
                       body: DynamicBody,
                       headers: Map[String, Seq[String]]) extends Put[DynamicBody] with DynamicRequest

object DynamicPut {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicPut = {
    DynamicPut(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicPut = apply(uri, body, new HeadersBuilder)(contextFactory)
}

@method("patch")
trait Patch[+B <: Body] extends Request[B] {
  override def method = Method.PATCH
}

abstract class StaticPatch[+B <: Body](initBody: B) extends Patch[B]

case class DynamicPatch(
                         uri: Uri,
                         body: DynamicBody,
                         headers: Map[String, Seq[String]]) extends Patch[DynamicBody] with DynamicRequest

object DynamicPatch {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicPatch = {
    DynamicPatch(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicPatch = apply(uri, body, new HeadersBuilder)(contextFactory)
}

@method("delete")
trait Delete[+B <: Body] extends Request[B] {
  override def method = Method.DELETE
}

abstract class StaticDelete[+B <: Body](initBody: B) extends Delete[B]

case class DynamicDelete(
                          uri: Uri,
                          body: DynamicBody,
                          headers: Map[String, Seq[String]]) extends Delete[DynamicBody] with DynamicRequest

object DynamicDelete {
  def apply(uri: Uri, body: DynamicBody, headersBuilder: HeadersBuilder)
           (implicit contextFactory: MessagingContextFactory): DynamicDelete = {
    DynamicDelete(uri, body, headersBuilder.withContext(contextFactory).result())
  }

  def apply(uri: Uri, body: DynamicBody)
           (implicit contextFactory: MessagingContextFactory): DynamicDelete = apply(uri, body, new HeadersBuilder)(contextFactory)
}

*/