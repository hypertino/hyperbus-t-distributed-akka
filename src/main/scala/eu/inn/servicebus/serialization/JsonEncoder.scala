package eu.inn.servicebus.serialization

import eu.inn.servicebus.serialization.impl.JsonSerializationMacro

import scala.language.experimental.macros

object JsonEncoder {
  def createEncoder[T]: Encoder[T] = macro JsonSerializationMacro.createEncoder[T]
}
