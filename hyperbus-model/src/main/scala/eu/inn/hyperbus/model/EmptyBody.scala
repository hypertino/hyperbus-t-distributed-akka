package eu.inn.hyperbus.model

import java.io.OutputStream

import eu.inn.binders.dynamic.{Null, Text, Obj, Value}
import eu.inn.hyperbus.model.annotations.contentType
import eu.inn.hyperbus.serialization.MessageSerializer

//@contentType("")
trait EmptyBody extends DynamicBody

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = None

  def content = Null

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): EmptyBody = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[Value]
    }
    EmptyBody
  }
}