package eu.inn.hyperbus.serialization

/**
 * Created by maqdev on 29.08.15.
 */
case class RequestHeader(url: String, method: String, contentType: Option[String], messageId: String, correlationId: Option[String])
