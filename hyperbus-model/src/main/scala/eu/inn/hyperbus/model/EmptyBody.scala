package eu.inn.hyperbus.model

import eu.inn.binders.dynamic.{Null, Value}

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