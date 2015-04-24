package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.protocol.{Request, Body, Message}
import eu.inn.hyperbus.serialization.impl.HyperSerializationMacro
import eu.inn.servicebus.serialization.Decoder

import scala.language.experimental.macros

object HyperJsonDecoder {
  def createRequestDecoder[T <: Request[Body]]: RequestDecoder = macro HyperSerializationMacro.createRequestDecoder[T]
}
