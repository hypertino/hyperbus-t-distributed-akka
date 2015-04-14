package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.serialization.impl.JsonSerializationMacro

import scala.language.experimental.macros

object JsonEncoder {
  def createEncoder[T]: Encoder[T] = macro JsonSerializationMacro.createEncoder[T]
}
