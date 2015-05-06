package eu.inn.hyperbus

import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.protocol._
import eu.inn.servicebus.serialization._

import scala.language.experimental.macros

package object serialization {
  type RequestDecoder = Function2[RequestHeader, JsonParser, Request[Body]]
  type ResponseDecoder = Function2[ResponseHeader, JsonParser, Response[Body]]
  type ResponseBodyDecoder = Function2[ResponseHeader, JsonParser, Body]
  type ResponseEncoder = Encoder[Response[Body]]

  def createRequestDecoder[T <: Request[Body]]: RequestDecoder = macro HyperSerializationMacro.createRequestDecoder[T]
  def createEncoder[T <: Message[_]]: Encoder[T] = macro HyperSerializationMacro.createEncoder[T]
  def createResponseBodyDecoder[T <: Body]: ResponseBodyDecoder = macro HyperSerializationMacro.createResponseBodyDecoder[T]
}
