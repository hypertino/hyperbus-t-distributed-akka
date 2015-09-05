package eu.inn.hyperbus.serialization

import java.io.OutputStream

import eu.inn.binders.core.BindOptions
import eu.inn.hyperbus.model.{Body, Request, Response}

object MessageSerializer {

  import eu.inn.binders.json._

  implicit val bindOptions = new BindOptions(true)

  def serializeRequest[B <: Body](request: Request[B], outputStream: OutputStream) = {
    val req = RequestHeader(request.url, request.method, request.body.contentType, request.messageId,
      if (request.messageId == request.correlationId) None else Some(request.correlationId)
    )
    writeUtf8("""{"request":""", outputStream)
    req.writeJson(outputStream)
    writeUtf8(""","body":""", outputStream)
    request.body.serialize(outputStream)
    writeUtf8("}", outputStream)
  }

  def serializeResponse[B <: Body](response: Response[B], outputStream: OutputStream) = {
    val resp = ResponseHeader(response.status, response.body.contentType, response.messageId,
      if (response.messageId == response.correlationId) None else Some(response.correlationId)
    )
    writeUtf8("""{"response":""", outputStream)
    resp.writeJson(outputStream)
    writeUtf8(""","body":""", outputStream)
    response.body.serialize(outputStream)
    writeUtf8("}", outputStream)
  }

  def writeUtf8(s: String, out: OutputStream) = {
    out.write(s.getBytes("UTF8"))
  }
}
