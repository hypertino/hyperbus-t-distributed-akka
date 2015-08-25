package eu.inn.hyperbus.rest.standard

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.annotations.response
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

@response(Status.OK) case class Ok[+B <: Body](body: B) extends NormalResponse with Response[B]

trait CreatedBody extends Body with Links {
  def location = links(DefLink.LOCATION)
}

@response(Status.CREATED) case class Created[+B <: CreatedBody](body: B) extends NormalResponse with Response[B]

case class DynamicCreatedBody(content: Value, contentType: Option[String] = None) extends DynamicBody with CreatedBody

@response(Status.ACCEPTED) case class Accepted[+B <: Body](body: B) extends NormalResponse with Response[B]

@response(Status.NON_AUTHORITATIVE_INFORMATION) case class NonAuthoritativeInformation[+B <: Body](body: B) extends NormalResponse with Response[B]

@response(Status.NO_CONTENT) case class NoContent[+B <: Body](body: B) extends NormalResponse with Response[B]

@response(Status.RESET_CONTENT) case class ResetContent[+B <: Body](body: B) extends NormalResponse with Response[B]

@response(Status.PARTIAL_CONTENT) case class PartialContent[+B <: Body](body: B) extends NormalResponse with Response[B]

@response(Status.MULTI_STATUS) case class MultiStatus[+B <: Body](body: B) extends NormalResponse with Response[B]

// ----------------- Redirect responses -----------------

// todo: URL for redirects like for created?

@response(Status.MULTIPLE_CHOICES) case class MultipleChoices[+B <: Body](body: B) extends RedirectResponse with Response[B]

@response(Status.MOVED_PERMANENTLY) case class MovedPermanently[+B <: Body](body: B) extends RedirectResponse with Response[B]

@response(Status.FOUND) case class Found[+B <: Body](body: B) extends RedirectResponse with Response[B]

@response(Status.SEE_OTHER) case class SeeOther[+B <: Body](body: B) extends RedirectResponse with Response[B]

@response(Status.NOT_MODIFIED) case class NotModified[+B <: Body](body: B) extends RedirectResponse with Response[B]

@response(Status.USE_PROXY) case class UseProxy[+B <: Body](body: B) extends RedirectResponse with Response[B]

@response(Status.TEMPORARY_REDIRECT) case class TemporaryRedirect[+B <: Body](body: B) extends RedirectResponse with Response[B]

// ----------------- Exception base classes -----------------

abstract class HyperBusException[+B <: ErrorBody](body: B)
  extends RuntimeException(body.toString) with Response[B] {
}

abstract class HyperBusServerException[+B <: ErrorBody](body: B) extends HyperBusException(body)

abstract class HyperBusClientException[+B <: ErrorBody](body: B) extends HyperBusException(body)

// ----------------- Client Error responses -----------------

@response(Status.BAD_REQUEST) case class BadRequest[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.UNAUTHORIZED) case class Unauthorized[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.PAYMENT_REQUIRED) case class PaymentRequired[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.FORBIDDEN) case class Forbidden[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.NOT_FOUND) case class NotFound[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response( Status.METHOD_NOT_ALLOWED) case class MethodNotAllowed[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.NOT_ACCEPTABLE) case class NotAcceptable[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.PROXY_AUTHENTICATION_REQUIRED) case class ProxyAuthenticationRequired[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.REQUEST_TIMEOUT) case class RequestTimeout[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.CONFLICT) case class Conflict[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.GONE) case class Gone[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.LENGTH_REQUIRED) case class LengthRequired[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.PRECONDITION_FAILED) case class PreconditionFailed[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.REQUEST_ENTITY_TOO_LARGE) case class RequestEntityTooLarge[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.REQUEST_URI_TOO_LONG) case class RequestUriTooLong[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.UNSUPPORTED_MEDIA_TYPE) case class UnsupportedMediaType[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.REQUESTED_RANGE_NOT_SATISFIABLE) case class RequestedRangeNotSatisfiable[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.EXPECTATION_FAILED) case class ExpectationFailed[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.UNPROCESSABLE_ENTITY) case class UnprocessableEntity[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.LOCKED) case class Locked[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.FAILED_DEPENDENCY) case class FailedDependency[+B <: ErrorBody](body: B) extends HyperBusClientException(body)

@response(Status.TOO_MANY_REQUEST) case class TooManyRequest[+B <: ErrorBody](body: B) extends HyperBusClientException(body)
// ----------------- Server Error responses -----------------

@response(Status.INTERNAL_SERVER_ERROR) case class InternalServerError[+B <: ErrorBody](body: B) extends HyperBusServerException(body)

@response(Status.NOT_IMPLEMENTED) case class NotImplemented[+B <: ErrorBody](body: B) extends HyperBusServerException(body)

@response(Status.BAD_GATEWAY) case class BadGateway[+B <: ErrorBody](body: B) extends HyperBusServerException(body)

@response(Status.SERVICE_UNAVAILABLE) case class ServiceUnavailable[+B <: ErrorBody](body: B) extends HyperBusServerException(body)

@response(Status.GATEWAY_TIMEOUT) case class GatewayTimeout[+B <: ErrorBody](body: B) extends HyperBusServerException(body)

@response(Status.HTTP_VERSION_NOT_SUPPORTED) case class HttpVersionNotSupported[+B <: ErrorBody](body: B) extends HyperBusServerException(body)

@response(Status.INSUFFICIENT_STORAGE) case class InsufficientStorage[+B <: ErrorBody](body: B) extends HyperBusServerException(body)