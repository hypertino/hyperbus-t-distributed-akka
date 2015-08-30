package eu.inn.hyperbus.serialization

case class RequestHeader(url: String, method: String, contentType: Option[String], messageId: String, correlationId: Option[String])
