package eu.inn.hyperbus

import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.rest._
import eu.inn.servicebus.serialization._

import scala.language.experimental.macros


package object serialization {
  // todo: eliminate this package object
  type RequestDecoder[T <: Request[Body]] = Function2[RequestHeader, JsonParser, T]
  type ResponseDecoder[T <: Response[Body]] = Function2[ResponseHeader, JsonParser, T]
  type ResponseBodyDecoder = Function2[ResponseHeader, JsonParser, Body]
  /*  type ResponseEncoder = Encoder[Response[Body]]

    def createRequestDecoder[T <: Request[Body]]: RequestDecoder[T] = macro HyperSerializationMacro.createRequestDecoder[T]

    def createResponseBodyDecoder[T <: Body]: ResponseBodyDecoder = macro HyperSerializationMacro.createResponseBodyDecoder[T]

    def createEncoder[T <: Message[_]]: Encoder[T] = macro HyperSerializationMacro.createEncoder[T]
  */
}
