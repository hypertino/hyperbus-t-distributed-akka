package eu.inn.hyperbus.rest.standard

import java.io.OutputStream

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.rest.Body
import eu.inn.hyperbus.rest.annotations.contentType
import eu.inn.hyperbus.serialization.MessageEncoder

@contentType("no-content")
trait EmptyBody extends Body

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = Some(ContentType.NO_CONTENT)
  def encode(outputStream: OutputStream): Unit = {
    MessageEncoder.writeUtf8("null", outputStream)
  }
  def decoder(contentType: Option[String], jsonParser : com.fasterxml.jackson.core.JsonParser): EmptyBody = {
    import eu.inn.binders.json._
      SerializerFactory.findFactory().withJsonParser(jsonParser) {deserializer =>
      deserializer.unbind[Value]
    }
    EmptyBody
  }
  def apply(contentType: Option[String], jsonParser : com.fasterxml.jackson.core.JsonParser): EmptyBody = decoder(contentType, jsonParser)
}