package eu.inn.hyperbus

import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization.HyperSerializationMacro
import eu.inn.servicebus.serialization._

import scala.language.experimental.macros

package object serialization {
  type RequestDecoder = Function2[RequestHeader, JsonParser, Request[Body]]
  type ResponseDecoder = Function2[ResponseHeader, JsonParser, Response[Body]]
  type ResponseBodyDecoder = Function2[ResponseHeader, JsonParser, Body]

  def createRequestDecoder[T <: Request[Body]]: RequestDecoder = macro HyperSerializationMacro.createRequestDecoder[T]
  def createEncoder[T <: Message[_]]: Encoder[T] = macro HyperSerializationMacro.createEncoder[T]
  def createResponseDecoder[T <: Response[Body]](bodyDecoder: ResponseBodyDecoder): ResponseDecoder = {
    (responseHeader: ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser) => {
      //todo: response visitor
      //todo: responsDecoder merge with responseBodyDecoder
      // todo: Generic Errors and Responses

      val body = bodyDecoder(responseHeader, responseBodyJson)
      responseHeader.status match {
        case Status.OK => Ok(body)
        case Status.CREATED => Created(body.asInstanceOf[CreatedBody])
        case Status.CONFLICT => ConflictError(body.asInstanceOf[ErrorBodyTrait])
        case Status.INTERNAL_ERROR => InternalError(body.asInstanceOf[ErrorBodyTrait])
      }
    }
  }

  def createResponseBodyDecoder[T <: Body]: ResponseBodyDecoder = macro HyperSerializationMacro.createResponseBodyDecoder[T]
}
