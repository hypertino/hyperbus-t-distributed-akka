package com.hypertino.hyperbus

import com.fasterxml.jackson.core.JsonParser
import com.hypertino.hyperbus.model.{Body, Request, Response}

package object serialization {
  type RequestDeserializer[+T <: Request[Body]] = Function2[RequestHeader, JsonParser, T]
  type ResponseDeserializer[+T <: Response[Body]] = Function2[ResponseHeader, JsonParser, T]
  type ResponseBodyDeserializer = Function2[Option[String], JsonParser, Body]
}
