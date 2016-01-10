package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.transport.api.Uri

case class RequestHeader(uri: Uri, method: String, contentType: Option[String], messageId: String, correlationId: Option[String])
