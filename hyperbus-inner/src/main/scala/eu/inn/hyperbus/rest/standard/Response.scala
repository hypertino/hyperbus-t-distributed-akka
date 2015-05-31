package eu.inn.hyperbus.rest.standard

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.rest._

object Status {
  val OK = 200
  val CREATED = 201

  val NOT_FOUND=404
  val CONFLICT=409

  val INTERNAL_ERROR=500
}

case class Ok[+B <: Body](body: B) extends Response[B] with NormalResponse[B] {
  override def status: Int = Status.OK
}

trait CreatedBody extends Body with Links {
  def location = links(DefLink.LOCATION)
}

case class Created[+B <: CreatedBody](body: B) extends Response[B] with NormalResponse[B] {
  override def status: Int = Status.CREATED
}

case class DynamicCreatedBody(content: Value, contentType: Option[String] = None) extends DynamicBody with CreatedBody

case class InternalError[+B <: ErrorBodyTrait](body: B)
  extends RuntimeException(body.message) with Response[B] with ServerError[B] {
  override def status: Int = Status.INTERNAL_ERROR
}

case class NotFound[+B <: ErrorBodyTrait](body: B)
  extends RuntimeException(body.message) with Response[B] with ClientError[B] {
  override def status: Int = Status.NOT_FOUND
}

case class Conflict[+B <: ErrorBodyTrait](body: B)
  extends RuntimeException(body.message) with Response[B] with ClientError[B] {
  override def status: Int = Status.CONFLICT
}

