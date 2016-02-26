package eu.inn.hyperbus.model

import scala.collection.mutable

object Header {
  val METHOD = "method"
  val CONTENT_TYPE = "contentType"
  val MESSAGE_ID = "messageId"
  val CORRELATION_ID = "correlationId"
  val REVISION = "revision"
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

  def result(): Map[String, Seq[String]] = {
    mapBuilder.result().filterNot(_._2.isEmpty)
  }
}
