package eu.inn.hyperbus

import com.fasterxml.jackson.core.JsonParser
import eu.inn.hyperbus.model.{Response, Body, Request}

package object serialization {
  type RequestDeserializer[T <: Request[Body]] = Function2[RequestHeader, JsonParser, T]
  type ResponseDeserializer[T <: Response[Body]] = Function2[ResponseHeader, JsonParser, T]
  type ResponseBodyDeserializer = Function2[Option[String], JsonParser, Body]
}
