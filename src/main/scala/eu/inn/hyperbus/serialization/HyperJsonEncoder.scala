package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.protocol.{Message}
import eu.inn.hyperbus.serialization.impl.HyperSerializationMacro
import eu.inn.servicebus.serialization.Encoder

import scala.language.experimental.macros

object HyperJsonEncoder {
  def createEncoder[T <: Message[_]]: Encoder[T] = macro HyperSerializationMacro.createEncoder[T]
}
