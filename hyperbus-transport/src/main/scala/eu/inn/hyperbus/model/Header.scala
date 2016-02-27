package eu.inn.hyperbus.model

import scala.collection.mutable

object Header {
  val METHOD = "method"
  val CONTENT_TYPE = "contentType"
  val MESSAGE_ID = "messageId"
  val CORRELATION_ID = "correlationId"
  val REVISION = "revision"
}

class Headers private[model] (private [this] val v: Map[String, Seq[String]]) extends Map[String, Seq[String]] {
  override def +[B1 >: Seq[String]](kv: (String, B1)): Map[String, B1] = new Headers(v + (kv._1 → seqOf(kv._2)))
  override def -(key: String): Map[String, Seq[String]] = new Headers(v - key)
  override def get(key: String): Option[Seq[String]] = v.get(key)
  override def iterator: Iterator[(String, Seq[String])] = v.iterator
  private def seqOf[B1 >: Seq[String]](b1: B1): Seq[String] = b1.asInstanceOf[Seq[String]] // todo: there have to be a better way
}

object Headers {
  def apply(vargs: (String, Seq[String])*)
           (implicit mcx: eu.inn.hyperbus.model.MessagingContextFactory): Headers = {
    val builder = new HeadersBuilder()
    builder ++= vargs
    builder withContext mcx result()
  }

  def apply(map: Map[String, Seq[String]])
           (implicit mcx: eu.inn.hyperbus.model.MessagingContextFactory): Headers = {
    new HeadersBuilder(map) withContext mcx result()
  }

  def apply()(implicit mcx: eu.inn.hyperbus.model.MessagingContextFactory): Headers = {
    new HeadersBuilder() withContext mcx result()
  }

  def unapply(headers: Headers): Option[Map[String, Seq[String]]] = Some(headers)

  def plain(headers: Map[String, Seq[String]]): Headers = {
    new HeadersBuilder(headers) result()
  }
}

class HeadersBuilder(private[this] val mapBuilder: mutable.Builder[(String, Seq[String]), Map[String, Seq[String]]]) {
  def this() = this(Map.newBuilder[String, Seq[String]])

  def this(headers: Map[String, Seq[String]]) = this {
    Map.newBuilder[String, Seq[String]] ++= headers
  }

  def +=(kv: (String, String)) = {
    mapBuilder += kv._1 → Seq(kv._2)
    this
  }

  def ++=(headers: Map[String, Seq[String]]) = {
    mapBuilder ++= headers
    this
  }

  def ++=(headers: Seq[(String, Seq[String])]) = {
    mapBuilder ++= headers
    this
  }

  def withCorrelation(messageId: String, correlationId: String) = {
    mapBuilder += eu.inn.hyperbus.model.Header.MESSAGE_ID -> Seq(messageId)
    if (messageId != correlationId) {
      mapBuilder += Header.CORRELATION_ID → Seq(correlationId)
    }
    else {
      mapBuilder += Header.CORRELATION_ID → Seq.empty[String]
    }
    this
  }

  def withContext(contextFactory: eu.inn.hyperbus.model.MessagingContextFactory) = {
    val ctxVal = contextFactory.newContext()
    withCorrelation(ctxVal.messageId, ctxVal.correlationId)
  }

  def withContentType(contentType: Option[String]): HeadersBuilder = {
    mapBuilder ++= contentType.map(ct => Header.CONTENT_TYPE → Seq(ct))
    this
  }

  def withMethod(method: String) = {
    mapBuilder += Header.METHOD → Seq(method)
    this
  }

  def result(): Headers = {
    new Headers(mapBuilder.result().filterNot(_._2.isEmpty))
  }
}
