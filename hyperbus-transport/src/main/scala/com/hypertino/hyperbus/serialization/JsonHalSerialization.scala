package com.hypertino.hyperbus.serialization

import com.hypertino.binders.json.api.{JsonGeneratorApi, JsonParserApi}
import com.hypertino.binders.json.{JsonBindersFactory, JsonDeserializerBase, JsonSerializerBase}
import com.hypertino.inflector.naming.Converter

class JsonHalSerializer[C <: Converter](jsonGenerator: JsonGeneratorApi) extends JsonSerializerBase[C, JsonHalSerializer[C]](jsonGenerator) {
  protected override def createFieldSerializer() = new JsonHalSerializer[C](jsonGenerator)

  //def writeEmbeddedResource[E <: Embedded[_ <: Body]](value: E): Unit = value.inner.serialize()
  //def writeEmbeddedResources[E <: Traversable[Embedded[_]]](value: E): Unit = ???
}

class JsonHalDeserializer[C <: Converter](jsonParser: JsonParserApi, override val moveToNextToken: Boolean = true, override val fieldName: Option[String] = None) extends JsonDeserializerBase[C, JsonHalDeserializer[C]](jsonParser, moveToNextToken, fieldName) {
  protected override def createFieldDeserializer(jsonParser: JsonParserApi, moveToNextToken: Boolean, fieldName: Option[String]) = new JsonHalDeserializer[C](jsonParser, moveToNextToken, fieldName)

  //def readEmbeddedResource[E <: Embedded[_]]() : E = ???
  //def readEmbeddedResources[E <: Traversable[Embedded[_]]](value: E): Unit = ???
}

class JsonHalSerializerFactory[C <: Converter] extends JsonBindersFactory[C, JsonHalSerializer[C], JsonHalDeserializer[C]] {
  def createSerializer(jsonGenerator: JsonGeneratorApi): JsonHalSerializer[C] = new JsonHalSerializer[C](jsonGenerator)

  def createDeserializer(jsonParser: JsonParserApi): JsonHalDeserializer[C] = new JsonHalDeserializer[C](jsonParser)
}
