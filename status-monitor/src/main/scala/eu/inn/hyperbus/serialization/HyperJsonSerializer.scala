package eu.inn.hyperbus.serialization

import com.fasterxml.jackson.core.{JsonParser, JsonGenerator}
import eu.inn.binders.json.{SerializerFactory, JsonDeserializerBase, JsonSerializerBase}
import eu.inn.binders.naming.Converter
import eu.inn.hyperbus.protocol.{Body, Link}

/*
class HyperJsonSerializer[C <: Converter](jsonGenerator: JsonGenerator) extends JsonSerializerBase[C, HyperJsonSerializer[C]](jsonGenerator) {
  protected override def createFieldSerializer() = new HyperJsonSerializer[C](jsonGenerator)

  def writeLinks(links: Body.Links) =
}

class HyperJsonDeserializer[C <: Converter] (jsonParser: JsonParser, override val moveToNextToken: Boolean = true, override val fieldName: Option[String] = None) extends JsonDeserializerBase[C, HyperJsonDeserializer[C]](jsonParser, moveToNextToken, fieldName) {

  protected override def createFieldDeserializer(jsonParser: JsonParser, moveToNextToken: Boolean, fieldName: Option[String]) = {
    val objectFieldName =
      fieldName match {
        case Some("_links") => Some("links")
        case _ => fieldName
      }
    new HyperJsonDeserializer[C](jsonParser, moveToNextToken, objectFieldName)
  }

  //def readLinks(): Body.Links = ???
}

class HyperJsonSerializerFactory[C <: Converter] extends SerializerFactory[C, HyperJsonSerializer[C], HyperJsonDeserializer[C]] {
  def createSerializer(jsonGenerator: JsonGenerator): HyperJsonSerializer[C] = new HyperJsonSerializer[C](jsonGenerator)
  def createDeserializer(jsonParser: JsonParser): HyperJsonDeserializer[C] = new HyperJsonDeserializer[C](jsonParser)
}
*/