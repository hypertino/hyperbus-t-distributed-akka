package eu.inn.hyperbus.serialization.impl

import java.io.{ByteArrayInputStream, InputStream, OutputStream}

import com.fasterxml.jackson.core.{JsonParser, JsonToken, JsonFactory}
import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.serialization.{DecodeException, ResponseHeader, RequestHeader}
import eu.inn.servicebus.serialization.{Encoder}

object Helpers {
  import eu.inn.binders.json._

  def encodeMessage[B <: Body](request: Request[B], bodyEncoder: Encoder[B], out: OutputStream) = {
    val req = RequestHeader(request.url, request.method, request.body.contentType)
    writeUtf8("""{"request":""",out)
    req.writeJson(out)
    writeUtf8(""","body":""",out)
    bodyEncoder(request.body, out)
    writeUtf8("}",out)
  }

  def encodeMessage[B <: Body](response: Response[B], bodyEncoder: Encoder[B], out: OutputStream) = {
    val resp = ResponseHeader(response.status, response.body.contentType)
    writeUtf8("""{"response":""",out)
    resp.writeJson(out)
    writeUtf8(""","body":""",out)
    bodyEncoder(response.body, out)
    writeUtf8("}",out)
  }

  def decodeRequestWith[B <: Body](inputStream: InputStream)(decoder: (RequestHeader, JsonParser) => Request[B]): Request[B] = {

    val jf = new JsonFactory()
    val jp = jf.createParser(inputStream) // todo: this move to SerializerFactory
    val factory = SerializerFactory.findFactory()
    try {
      expect(jp, JsonToken.START_OBJECT)
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName = jp.getCurrentName
      val requestHeader =
        if (fieldName == "request") {
          factory.withJsonParser(jp) { deserializer=>
            deserializer.unbind[RequestHeader]
          }
        } else {
          throw DecodeException(s"'request' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName2 = jp.getCurrentName
      val result =
        if (fieldName2 == "body") {
          decoder(requestHeader, jp)
        } else {
          throw DecodeException(s"'body' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.END_OBJECT)
      result
    } finally {
      jp.close()
    }
  }

  def decodeResponseWith[B <: Body](inputStream: InputStream)(decoder: (ResponseHeader, JsonParser) => Response[B]): Response[B] = {
    val jf = new JsonFactory()
    val jp = jf.createParser(inputStream) // todo: this move to SerializerFactory
    val factory = SerializerFactory.findFactory()
    try {
      expect(jp, JsonToken.START_OBJECT)
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName = jp.getCurrentName
      val responseHeader =
        if (fieldName == "response") {
          factory.withJsonParser(jp) { deserializer=>
            deserializer.unbind[ResponseHeader]
          }
        } else {
          throw DecodeException(s"'response' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName2 = jp.getCurrentName
      val result =
        if (fieldName2 == "body") {
          decoder(responseHeader, jp)
        } else {
          throw DecodeException(s"'body' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.END_OBJECT)
      result
    } finally {
      jp.close()
    }
  }

  def decodeDynamicRequest(requestHeader: RequestHeader, jsonParser: JsonParser): Request[Body] = {
    val body = SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      DynamicBody(deserializer.unbind[Value], requestHeader.contentType)
    }

    requestHeader.method match {
      case StandardMethods.GET => DynamicGet(requestHeader.url, body)
      case StandardMethods.POST => DynamicPost(requestHeader.url, body)
      case StandardMethods.PUT => DynamicPut(requestHeader.url, body)
      case StandardMethods.DELETE => DynamicDelete(requestHeader.url, body)
      case StandardMethods.PATCH => DynamicPatch(requestHeader.url, body)
      case _ => throw new RuntimeException(s"Unknown method: '${requestHeader.method}'") //todo: exception class and save more details
    }
  }

  def decodeDynamicResponseBody(responseHeader: ResponseHeader, jsonParser: JsonParser): DynamicBody = {
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      DynamicBody(deserializer.unbind[Value], responseHeader.contentType)
    }
  }

  def decodeErrorResponseBody(responseHeader: ResponseHeader, jsonParser: JsonParser): ErrorBody = {
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[ErrorBody]
    }
  }

  private def expect(parser: JsonParser, token: JsonToken) = {
    val loc = parser.getCurrentLocation
    val next = parser.nextToken()
    if (next != token) throw DecodeException(s"$token expected at $loc, but found: $next")
  }

  private def writeUtf8(s:String, out: OutputStream) = {
    out.write(s.getBytes("UTF8"))
  }
}
