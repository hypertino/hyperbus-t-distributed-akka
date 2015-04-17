package eu.inn.hyperbus.serialization

import eu.inn.servicebus.serialization.Decoder
import eu.inn.servicebus.serialization.impl.JsonSerializationMacro

import scala.language.experimental.macros

object HyperJsonDecoder {
  def createDecoder[T]: Decoder[T] = macro JsonSerializationMacro.createDecoder[T]
}
