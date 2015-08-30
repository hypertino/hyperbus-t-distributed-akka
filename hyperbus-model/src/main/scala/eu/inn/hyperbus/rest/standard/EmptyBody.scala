package eu.inn.hyperbus.rest.standard

import java.io.OutputStream

import eu.inn.hyperbus.rest.Body
import eu.inn.hyperbus.rest.annotations.contentType
import eu.inn.hyperbus.serialization.MessageEncoder

@contentType("no-content")
trait EmptyBody extends Body

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = Some(ContentType.NO_CONTENT)
  def encode(outputStream: OutputStream): Unit = {
    MessageEncoder.writeUtf8("null", outputStream)
  }
}