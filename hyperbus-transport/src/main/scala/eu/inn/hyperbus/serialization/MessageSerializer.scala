package eu.inn.hyperbus.serialization

import java.io.OutputStream

import eu.inn.binders.core.BindOptions
import eu.inn.hyperbus.model.{Body, Request, Response}
import eu.inn.hyperbus.transport.api.uri.UriJsonSerializer

object MessageSerializer {

  import eu.inn.binders.json._
  implicit val bindOptions = new BindOptions(true)
  implicit val uriJsonSerializer = new UriJsonSerializer

  def serializeRequest[B <: Body](request: Request[B], outputStream: OutputStream): Unit = {
    require(request.headers, "method")
    require(request.headers, "messageId")
    //val req = request.uri, request.headers)
    writeUtf8("""{"uri":""", outputStream)
    request.uri.writeJson(outputStream)
    writeUtf8(""","headers":""", outputStream)
    request.headers.writeJson(outputStream)
    writeUtf8(""","body":""", outputStream)
    request.body.serialize(outputStream)
    writeUtf8("}", outputStream)
  }

  def serializeResponse[B <: Body](response: Response[B], outputStream: OutputStream) = {
    require(response.headers, "messageId")
    //val resp = ResponseHeader(response.statusCode, response.headers)
    writeUtf8("""{"status":""", outputStream)
    response.statusCode.writeJson(outputStream)
    writeUtf8(""","headers":""", outputStream)
    response.headers.writeJson(outputStream)
    writeUtf8(""","body":""", outputStream)
    response.body.serialize(outputStream)
    writeUtf8("}", outputStream)
  }

  def require(headers: Map[String, Seq[String]], required: String) = {
    if (headers.get(required).flatMap(_.headOption).isEmpty)
      throw new HeaderIsRequiredException(required)
  }

  def writeUtf8(s: String, out: OutputStream) = {
    out.write(s.getBytes("UTF8"))
  }
}

