package eu.inn.hyperbus.serialization.impl

import java.io.{InputStream, OutputStream}

import com.fasterxml.jackson.core.{JsonParser, JsonToken, JsonFactory}
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.protocol.{Post, Response, Body, Request}
import eu.inn.hyperbus.serialization.{DecodeException, ResponseHeader, RequestHeader}
import eu.inn.servicebus.serialization.{Encoder, Decoder}



object Helpers {

  def encodeMessage[B <: Body](request: Request[B], b: B, bodyEncoder: Encoder[B], out: OutputStream) = {
    import eu.inn.binders.json._
    val req = RequestHeader(request.url, request.method, request.body.contentType)
    writeUtf8("""{"request":""",out)
    req.writeJson(out)
    writeUtf8(""","body":""",out)
    bodyEncoder.encode(b, out)
    writeUtf8("}",out)
  }

  def encodeMessage[B <: Body](response: Response[B], b: B, bodyEncoder: Encoder[B], out: OutputStream) = {
    import eu.inn.binders.json._
    val resp = ResponseHeader(response.status)
    writeUtf8("""{"response":""",out)
    resp.writeJson(out)
    writeUtf8(""","body":""",out)
    bodyEncoder.encode(b, out)
    writeUtf8("}",out)
  }

  def decodeMessage[B <: Body](inputStream: InputStream,
                               decoder: (RequestHeader, InputStream) => Request[B]): Request[B] = {

    import eu.inn.binders.json._
    val jf = new JsonFactory()
    val jp = jf.createParser(inputStream) // todo: this move to SerializerFactory
    try {
      expect(jp, JsonToken.START_OBJECT)
      expect(jp, JsonToken.FIELD_NAME)
      val fieldName = jp.getCurrentName
      val requestHeader =
        if (fieldName == "request") {
          inputStream.readJson[RequestHeader]
        } else {
          throw DecodeException(s"'request' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.FIELD_NAME)
      val result =
        if (fieldName == "body") {
          decoder(requestHeader, inputStream)
        } else {
          throw DecodeException(s"'body' field expected, but found: '$fieldName'")
        }
      expect(jp, JsonToken.END_OBJECT)
      result
    } finally {
      jp.close()
    }
  }

  def expect(parser: JsonParser, token: JsonToken) = {
    val loc = parser.getCurrentLocation
    val next = parser.nextToken()
    if (next != token) throw DecodeException(s"$token expected at $loc, but found: $next")
  }

  def writeUtf8(s:String, out: OutputStream) = {
    out.write(s.getBytes("UTF8"))
  }
}
