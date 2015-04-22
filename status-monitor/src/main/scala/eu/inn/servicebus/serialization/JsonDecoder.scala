package eu.inn.servicebus.serialization

import eu.inn.servicebus.serialization.impl.JsonSerializationMacro

import scala.language.experimental.macros

object JsonDecoder {
  def createDecoder[T]: Decoder[T] = macro JsonSerializationMacro.createDecoder[T]
}
