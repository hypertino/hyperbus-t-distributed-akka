package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.transport.api.uri.Uri

case class RequestHeader(uri: Uri,
                         method: String,
                         contentType: Option[String],
                         messageId: String,
                         correlationId: Option[String],
                         headers: Map[String, Seq[String]])
