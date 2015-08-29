package eu.inn.hyperbus.serialization

/**
 * Created by maqdev on 29.08.15.
 */
case class ResponseHeader(status: Int, contentType: Option[String], messageId: String, correlationId: Option[String])
