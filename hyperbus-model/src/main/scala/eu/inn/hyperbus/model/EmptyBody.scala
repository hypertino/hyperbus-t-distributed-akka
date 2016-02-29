package eu.inn.hyperbus.model

import java.io.OutputStream

import eu.inn.binders.dynamic.{Text, Obj, Value}
import eu.inn.hyperbus.model.annotations.contentType
import eu.inn.hyperbus.serialization.MessageSerializer

//@contentType("")
trait EmptyBody extends DynamicBody

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = None

  def content = Obj(
    Map(
      "code" → Text(code),
      "errorId" → Text(errorId)
    )
      ++ description.map(s ⇒ "description" → Text(s))
      ++ contentType.map(s ⇒ "contentType" → Text(s))
      ++ {if(extra.isDefined) Some("extra" → extra) else None}
  )

  override def serialize(outputStream: OutputStream): Unit = {
    MessageSerializer.writeUtf8("null", outputStream)
  }

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): EmptyBody = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[Value]
    }
    EmptyBody
  }
}