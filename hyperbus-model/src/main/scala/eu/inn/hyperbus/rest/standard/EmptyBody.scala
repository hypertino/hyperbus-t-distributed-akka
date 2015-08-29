package eu.inn.hyperbus.rest.standard

import eu.inn.hyperbus.rest.Body
import eu.inn.hyperbus.rest.annotations.contentType

@contentType("no-content")
trait EmptyBody extends Body

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = Some(ContentType.NO_CONTENT)
}