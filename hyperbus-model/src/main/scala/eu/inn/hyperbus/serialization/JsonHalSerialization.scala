package eu.inn.hyperbus.serialization

import com.fasterxml.jackson.core.{JsonParser, JsonGenerator}
import eu.inn.binders.json.{SerializerFactory, JsonDeserializerBase, JsonSerializerBase}
import eu.inn.binders.naming.Converter

class JsonHalSerializer[C <: Converter](jsonGenerator: JsonGenerator) extends JsonSerializerBase[C, JsonHalSerializer[C]](jsonGenerator) {
  protected override def createFieldSerializer() = new JsonHalSerializer[C](jsonGenerator)

  //def writeEmbeddedResource[E <: Embedded[_ <: Body]](value: E): Unit = value.inner.serialize()
  //def writeEmbeddedResources[E <: Traversable[Embedded[_]]](value: E): Unit = ???
}

class JsonHalDeserializer[C <: Converter] (jsonParser: JsonParser, override val moveToNextToken: Boolean = true, override val fieldName: Option[String] = None) extends JsonDeserializerBase[C, JsonHalDeserializer[C]](jsonParser, moveToNextToken, fieldName) {
  protected override def createFieldDeserializer(jsonParser: JsonParser, moveToNextToken: Boolean, fieldName: Option[String]) = new JsonHalDeserializer[C](jsonParser, moveToNextToken, fieldName)

  //def readEmbeddedResource[E <: Embedded[_]]() : E = ???
  //def readEmbeddedResources[E <: Traversable[Embedded[_]]](value: E): Unit = ???
}

class JsonHalSerializerFactory[C <: Converter] extends SerializerFactory[C, JsonHalSerializer[C], JsonHalDeserializer[C]] {
  def createSerializer(jsonGenerator: JsonGenerator): JsonHalSerializer[C] = new JsonHalSerializer[C](jsonGenerator)
  def createDeserializer(jsonParser: JsonParser): JsonHalDeserializer[C] = new JsonHalDeserializer[C](jsonParser)
}
