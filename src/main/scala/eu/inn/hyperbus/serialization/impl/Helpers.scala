package eu.inn.hyperbus.serialization.impl

import java.io.{InputStream, OutputStream}

import eu.inn.hyperbus.protocol.{Post, Response, Body, Request}
import eu.inn.servicebus.serialization.{Encoder, Decoder}

object Helpers {

  case class RequestHeader(url:String, method:String, contentType:Option[String])
  case class ResponseHeader(status:Int)

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

  def decodeMessage[B <: Body](in: InputStream, bodyDecoder: Decoder[B]) = ???/*{
    new Post(bodyDecoder.decode(in)) {
      def url = ""
    }
  }*/

  def writeUtf8(s:String, out: OutputStream) = {
    out.write(s.getBytes("UTF8"))
  }
}
