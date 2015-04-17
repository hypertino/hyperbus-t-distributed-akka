package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.protocol.Message
import eu.inn.hyperbus.serialization.impl.HyperSerializationMacro
import eu.inn.servicebus.serialization.Decoder

import scala.language.experimental.macros

object HyperJsonDecoder {
  def createDecoder[T <: Message[_]]: Decoder[T] = macro HyperSerializationMacro.createDecoder[T]
}
