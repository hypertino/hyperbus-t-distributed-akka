package eu.inn.hyperbus.serialization

import eu.inn.servicebus.serialization.Encoder
import eu.inn.servicebus.serialization.impl.JsonSerializationMacro

import scala.language.experimental.macros

object HyperJsonEncoder {
  def createEncoder[T]: Encoder[T] = macro JsonSerializationMacro.createEncoder[T]
}
