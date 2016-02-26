package eu.inn.hyperbus.serialization

import java.io.InputStream

import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import eu.inn.binders.core.BindOptions
import eu.inn.hyperbus.model.{Body, Request, Response}
import eu.inn.hyperbus.transport.api.uri.UriJsonDeserializer

object MessageDeserializer {

  import eu.inn.binders.json._

  implicit val bindOptions = new BindOptions(true)
  implicit val uriJsonDeserializer = new UriJsonDeserializer

  def deserializeRequestWith[REQ <: Request[Body]](inputStream: InputStream)(deserializer: RequestDeserializer[REQ]): REQ = {
    val jf = new JsonFactory()
    val jp = jf.createParser(inputStream) // todo: this move to SerializerFactory
    val factory = SerializerFactory.findFactory()
    try {
      expect(jp, JsonToken.START_OBJECT)
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName = jp.getCurrentName
      val requestHeader =
        if (fieldName == "request") {
          factory.withJsonParser(jp) { deserializer =>
            deserializer.unbind[RequestHeader]
          }
        } else {
          throw DeserializeException(s"'request' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName2 = jp.getCurrentName
      val result =
        if (fieldName2 == "body") {
          deserializer(requestHeader, jp)
        } else {
          throw DeserializeException(s"'body' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.END_OBJECT)
      result
    } finally {
      jp.close()
    }
  }

  def deserializeResponseWith[RESP <: Response[Body]](inputStream: InputStream)(deserializer: ResponseDeserializer[RESP]): RESP = {
    val jf = new JsonFactory()
    val jp = jf.createParser(inputStream) // todo: this move to SerializerFactory
    val factory = SerializerFactory.findFactory()
    try {
      expect(jp, JsonToken.START_OBJECT)
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName = jp.getCurrentName
      val responseHeader =
        if (fieldName == "response") {
          factory.withJsonParser(jp) { deserializer =>
            deserializer.unbind[ResponseHeader]
          }
        } else {
          throw DeserializeException(s"'response' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName2 = jp.getCurrentName
      val result =
        if (fieldName2 == "body") {
          deserializer(responseHeader, jp)
        } else {
          throw DeserializeException(s"'body' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.END_OBJECT)
      result
    } finally {
      jp.close()
    }
  }

  private def expect(parser: JsonParser, token: JsonToken) = {
    val loc = parser.getCurrentLocation
    val next = parser.nextToken()
    if (next != token) throw DeserializeException(s"$token expected at $loc, but found: $next")
  }
}
