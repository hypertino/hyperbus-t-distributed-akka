package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.serialization.impl.JsonSerializationMacro

import scala.language.experimental.macros

object JsonDecoder {
  def createDecoder[T]: Decoder[T] = macro JsonSerializationMacro.createDecoder[T]
}
