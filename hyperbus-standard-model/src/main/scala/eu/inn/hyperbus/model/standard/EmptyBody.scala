package eu.inn.hyperbus.model.standard

import java.io.OutputStream

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.model.Body
import eu.inn.hyperbus.model.annotations.contentType
import eu.inn.hyperbus.serialization.MessageSerializer

@contentType("no-content")
trait EmptyBody extends Body

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = Some(ContentType.NO_CONTENT)

  def serialize(outputStream: OutputStream): Unit = {
    MessageSerializer.writeUtf8("null", outputStream)
  }

  def deserializer(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): EmptyBody = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[Value]
    }
    EmptyBody
  }

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): EmptyBody = deserializer(contentType, jsonParser)
}