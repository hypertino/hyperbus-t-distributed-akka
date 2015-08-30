package eu.inn.hyperbus.serialization.impl

import java.io.{InputStream, OutputStream}

import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import eu.inn.binders.core.BindOptions
import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.standard.{DynamicCreatedBody, Status}
import eu.inn.hyperbus.serialization.{DecodeException, RequestHeader, ResponseHeader}
import eu.inn.servicebus.serialization.Encoder

/*
object InnerHelpers { // todo: rename this

  import eu.inn.binders.json._



  def decodeDynamicResponseBody(responseHeader: ResponseHeader, jsonParser: JsonParser): DynamicBody = {
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      val v = deserializer.unbind[Value]
      responseHeader.status match {
        case Status.CREATED => DynamicCreatedBody(v, responseHeader.contentType)
        case _ => DynamicBody(v, responseHeader.contentType)
      }
    }
  }

  def decodeEmptyResponseBody(responseHeader: ResponseHeader, jsonParser: JsonParser): EmptyBody = {
    decodeDynamicResponseBody(responseHeader, jsonParser)
    EmptyBody
  }

  def decodeErrorResponseBody(responseHeader: ResponseHeader, jsonParser: JsonParser): ErrorBody = {
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[ErrorBody]
    }
  }

  def decodeDynamicBody(requestHeader: RequestHeader, jsonParser: JsonParser): DynamicBody = {
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      val v = deserializer.unbind[Value]
      DynamicBody(v, requestHeader.contentType)
    }
  }

  def decodeEmptyBody(requestHeader: RequestHeader, requestBodyJson: JsonParser): EmptyBody = {
    decodeDynamicBody(requestHeader, requestBodyJson)
    EmptyBody
  }

  /*def dynamicBodyEncoder(body: DynamicBody, out: OutputStream): Unit =
    eu.inn.servicebus.serialization.createEncoder[Value](body.content, out)*/

  def emptyBodyEncoder(body: EmptyBody, out: OutputStream): Unit =
    eu.inn.servicebus.serialization.createEncoder[String](null, out)

  def errorBodyEncoder(body: ErrorBody, out: OutputStream): Unit =
    eu.inn.servicebus.serialization.createEncoder[ErrorBody](body, out)




}
*/