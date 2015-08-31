package eu.inn.hyperbus

import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.rest.{Body, Request, Response}

package object serialization {
  type RequestDecoder[T <: Request[Body]] = Function2[RequestHeader, JsonParser, T]
  type ResponseDecoder[T <: Response[Body]] = Function2[ResponseHeader, JsonParser, T]
  type ResponseBodyDecoder = Function2[Option[String], JsonParser, Body]
}
