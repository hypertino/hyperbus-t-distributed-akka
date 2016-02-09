package eu.inn.hyperbus.serialization

case class ResponseHeader(status: Int,
                          contentType: Option[String],
                          messageId: String,
                          correlationId: Option[String],
                          headers: Map[String, Seq[String]])
