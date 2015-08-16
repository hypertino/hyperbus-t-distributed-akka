package eu.inn.hyperbus.rest.standard

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.utils.IdUtils

//todo: !format code

object Status {
  val OK = 200
  val CREATED = 201
  val ACCEPTED = 202
  val NON_AUTHORITATIVE_INFORMATION = 203
  val NO_CONTENT = 204
  val RESET_CONTENT = 205
  val PARTIAL_CONTENT = 206
  val MULTI_STATUS = 207

  val MULTIPLE_CHOICES = 300
  val MOVED_PERMANENTLY = 301
  val FOUND = 302
  val SEE_OTHER = 303
  val NOT_MODIFIED = 304
  val USE_PROXY = 305
  val TEMPORARY_REDIRECT = 307

  val BAD_REQUEST = 400
  val UNAUTHORIZED = 401
  val PAYMENT_REQUIRED = 402
  val FORBIDDEN = 403
  val NOT_FOUND = 404
  val METHOD_NOT_ALLOWED = 405
  val NOT_ACCEPTABLE = 406
  val PROXY_AUTHENTICATION_REQUIRED = 407
  val REQUEST_TIMEOUT = 408
  val CONFLICT = 409
  val GONE = 410
  val LENGTH_REQUIRED = 411
  val PRECONDITION_FAILED = 412
  val REQUEST_ENTITY_TOO_LARGE = 413
  val REQUEST_URI_TOO_LONG = 414
  val UNSUPPORTED_MEDIA_TYPE = 415
  val REQUESTED_RANGE_NOT_SATISFIABLE = 416
  val EXPECTATION_FAILED = 417
  val UNPROCESSABLE_ENTITY = 422
  val LOCKED = 423
  val FAILED_DEPENDENCY = 424
  val TOO_MANY_REQUEST = 429

  val INTERNAL_SERVER_ERROR = 500
  val NOT_IMPLEMENTED = 501
  val BAD_GATEWAY = 502
  val SERVICE_UNAVAILABLE = 503
  val GATEWAY_TIMEOUT = 504
  val HTTP_VERSION_NOT_SUPPORTED = 505
  val INSUFFICIENT_STORAGE = 507
}

// ----------------- Normal responses -----------------

case class Ok[+B <: Body](body: B,
                          messageId: String = IdUtils.createId,
                          correlationId: Option[String] = MessagingContext.correlationId
                          ) extends NormalResponse with Response[B] {
  def status: Int = Status.OK
}

trait CreatedBody extends Body with Links {
  def location = links(DefLink.LOCATION)
}

case class Created[+B <: CreatedBody](body: B,
                                      messageId: String = IdUtils.createId,
                                      correlationId: Option[String] = MessagingContext.correlationId) extends NormalResponse with Response[B] {
  def status: Int = Status.CREATED
}

case class DynamicCreatedBody(content: Value, contentType: Option[String] = None) extends DynamicBody with CreatedBody

case class Accepted[+B <: Body](body: B,
                                messageId: String = IdUtils.createId,
                                correlationId: Option[String] = MessagingContext.correlationId) extends NormalResponse with Response[B] {
  def status: Int = Status.ACCEPTED
}

case class NonAuthoritativeInformation[+B <: Body](body: B,
                                                   messageId: String = IdUtils.createId,
                                                   correlationId: Option[String] = MessagingContext.correlationId) extends NormalResponse with Response[B] {
  def status: Int = Status.NON_AUTHORITATIVE_INFORMATION
}

case class NoContent[+B <: Body](body: B = EmptyBody,
                                 messageId: String = IdUtils.createId,
                                 correlationId: Option[String] = MessagingContext.correlationId) extends NormalResponse with Response[B] {
  def status: Int = Status.NO_CONTENT
}

case class ResetContent[+B <: Body](body: B,
                                    messageId: String = IdUtils.createId,
                                    correlationId: Option[String] = MessagingContext.correlationId) extends NormalResponse with Response[B] {
  def status: Int = Status.RESET_CONTENT
}

case class PartialContent[+B <: Body](body: B,
                                      messageId: String = IdUtils.createId,
                                      correlationId: Option[String] = MessagingContext.correlationId) extends NormalResponse with Response[B] {
  def status: Int = Status.PARTIAL_CONTENT
}

case class MultiStatus[+B <: Body](body: B,
                                   messageId: String = IdUtils.createId,
                                   correlationId: Option[String] = MessagingContext.correlationId) extends NormalResponse with Response[B] {
  def status: Int = Status.MULTI_STATUS
}

// ----------------- Redirect responses -----------------

// todo: URL for redirects like for created?

case class MultipleChoices[+B <: Body](body: B,
                                       messageId: String = IdUtils.createId,
                                       correlationId: Option[String] = MessagingContext.correlationId) extends RedirectResponse with Response[B] {
  def status: Int = Status.MULTIPLE_CHOICES
}

case class MovedPermanently[+B <: Body](body: B,
                                        messageId: String = IdUtils.createId,
                                        correlationId: Option[String] = MessagingContext.correlationId) extends RedirectResponse with Response[B] {
  def status: Int = Status.MOVED_PERMANENTLY
}

case class Found[+B <: Body](body: B,
                             messageId: String = IdUtils.createId,
                             correlationId: Option[String] = MessagingContext.correlationId) extends RedirectResponse with Response[B] {
  def status: Int = Status.FOUND
}

case class SeeOther[+B <: Body](body: B,
                                messageId: String = IdUtils.createId,
                                correlationId: Option[String] = MessagingContext.correlationId) extends RedirectResponse with Response[B] {
  def status: Int = Status.SEE_OTHER
}

case class NotModified[+B <: Body](body: B,
                                   messageId: String = IdUtils.createId,
                                   correlationId: Option[String] = MessagingContext.correlationId) extends RedirectResponse with Response[B] {
  def status: Int = Status.NOT_MODIFIED
}

case class UseProxy[+B <: Body](body: B,
                                messageId: String = IdUtils.createId,
                                correlationId: Option[String] = MessagingContext.correlationId) extends RedirectResponse with Response[B] {
  def status: Int = Status.USE_PROXY
}

case class TemporaryRedirect[+B <: Body](body: B,
                                         messageId: String = IdUtils.createId,
                                         correlationId: Option[String] = MessagingContext.correlationId) extends RedirectResponse with Response[B] {
  def status: Int = Status.TEMPORARY_REDIRECT
}

// ----------------- Exception base classes -----------------

abstract class HyperBusException[+B <: ErrorBody](body: B, cause: Throwable = null,
                                                  correlationId: Option[String] = MessagingContext.correlationId)
  extends RuntimeException(body.toString, cause) with Response[B] {
  def messageId: String = body.errorId
}

abstract class HyperBusServerException[+B <: ErrorBody](body: B, cause: Throwable = null,
                                                        correlationId: Option[String] = MessagingContext.correlationId) extends HyperBusException(body, cause)

abstract class HyperBusClientException[+B <: ErrorBody](body: B, cause: Throwable = null,
                                                        correlationId: Option[String] = MessagingContext.correlationId) extends HyperBusException(body, cause)

// ----------------- Client Error responses -----------------


case class BadRequest[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.BAD_REQUEST
}

case class Unauthorized[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.UNAUTHORIZED
}

case class PaymentRequired[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.PAYMENT_REQUIRED
}

case class Forbidden[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.FORBIDDEN
}

case class NotFound[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.NOT_FOUND
}

case class MethodNotAllowed[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.METHOD_NOT_ALLOWED
}

case class NotAcceptable[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.NOT_ACCEPTABLE
}

case class ProxyAuthenticationRequired[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.PROXY_AUTHENTICATION_REQUIRED
}

case class RequestTimeout[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.REQUEST_TIMEOUT
}

case class Conflict[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.CONFLICT
}

case class Gone[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.GONE
}

case class LengthRequired[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.LENGTH_REQUIRED
}

case class PreconditionFailed[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.PRECONDITION_FAILED
}

case class RequestEntityTooLarge[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.REQUEST_ENTITY_TOO_LARGE
}

case class RequestUriTooLong[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.REQUEST_URI_TOO_LONG
}

case class UnsupportedMediaType[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.UNSUPPORTED_MEDIA_TYPE
}

case class RequestedRangeNotSatisfiable[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.REQUESTED_RANGE_NOT_SATISFIABLE
}

case class ExpectationFailed[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.EXPECTATION_FAILED
}

case class UnprocessableEntity[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.UNPROCESSABLE_ENTITY
}

case class Locked[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.LOCKED
}

case class FailedDependency[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.FAILED_DEPENDENCY
}

case class TooManyRequest[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                        ) extends HyperBusClientException(body, cause, correlationId) {
  def status: Int = Status.TOO_MANY_REQUEST
}

// ----------------- Server Error responses -----------------

case class InternalServerError[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                                 ) extends HyperBusServerException(body, cause, correlationId) {
  def status: Int = Status.INTERNAL_SERVER_ERROR
}

case class NotImplemented[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                                 ) extends HyperBusServerException(body, cause, correlationId) {
  def status: Int = Status.NOT_IMPLEMENTED
}

case class BadGateway[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                                 ) extends HyperBusServerException(body, cause, correlationId) {
  def status: Int = Status.BAD_GATEWAY
}

case class ServiceUnavailable[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                                 ) extends HyperBusServerException(body, cause, correlationId) {
  def status: Int = Status.SERVICE_UNAVAILABLE
}

case class GatewayTimeout[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                                 ) extends HyperBusServerException(body, cause, correlationId) {
  def status: Int = Status.GATEWAY_TIMEOUT
}

case class HttpVersionNotSupported[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                                 ) extends HyperBusServerException(body, cause, correlationId) {
  def status: Int = Status.HTTP_VERSION_NOT_SUPPORTED
}

case class InsufficientStorage[+B <: ErrorBody](body: B, cause: Throwable = null, correlationId: Option[String] = MessagingContext.correlationId
                                                 ) extends HyperBusServerException(body, cause, correlationId) {
  def status: Int = Status.INSUFFICIENT_STORAGE
}
