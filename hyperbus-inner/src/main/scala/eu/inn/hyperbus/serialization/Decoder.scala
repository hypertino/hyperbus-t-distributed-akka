package eu.inn.hyperbus.serialization

case class RequestHeader(url: String, method: String, contentType: Option[String], messageId: String, correlationId: Option[String])

case class ResponseHeader(status: Int, contentType: Option[String], messageId: String, correlationId: Option[String])

case class DecodeException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)