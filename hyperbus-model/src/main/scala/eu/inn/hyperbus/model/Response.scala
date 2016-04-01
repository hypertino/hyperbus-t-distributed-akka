package eu.inn.hyperbus.model

import eu.inn.binders.value.Value
import eu.inn.hyperbus.model.annotations.response

trait NormalResponse extends Response[Body]

trait RedirectResponse extends Response[Body]

trait ErrorResponse extends Response[ErrorBody]

trait ServerError extends ErrorResponse

trait ClientError extends ErrorResponse

// ----------------- Normal responses -----------------

@response(Status.OK) case class Ok[+B <: Body](body: B) extends NormalResponse with Response[B]

trait CreatedBody extends Body with Links {
  def location: Link = links(DefLink.LOCATION) match {
    case Left(link) ⇒ link
    case Right(sequence) ⇒ sequence.head
  }
}

@response(Status.CREATED) case class Created[+B <: CreatedBody](body: B) extends NormalResponse with Response[B]


object DynamicCreatedBody {
  def apply(contentType: Option[String], content: Value): DynamicBody with CreatedBody = DynamicCreatedBodyContainer(contentType, content)

  def apply(content: Value): DynamicBody with CreatedBody = apply(None, content)

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): DynamicBody with CreatedBody = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(contentType, deserializer.unbind[Value])
    }
  }

  def unapply(dynamicBody: DynamicBody with CreatedBody) = Some((dynamicBody.contentType, dynamicBody.content))
}

private[model] case class DynamicCreatedBodyContainer(contentType: Option[String], content: Value) extends DynamicBody with CreatedBody

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

abstract class HyperbusException[+B <: ErrorBody](body: B)
  extends RuntimeException(body.toString) with Response[B] {
}

abstract class HyperbusServerException[+B <: ErrorBody](body: B) extends HyperbusException(body)

abstract class HyperbusClientException[+B <: ErrorBody](body: B) extends HyperbusException(body)

// ----------------- Client Error responses -----------------

@response(Status.BAD_REQUEST) case class BadRequest[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.UNAUTHORIZED) case class Unauthorized[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.PAYMENT_REQUIRED) case class PaymentRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.FORBIDDEN) case class Forbidden[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.NOT_FOUND) case class NotFound[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.METHOD_NOT_ALLOWED) case class MethodNotAllowed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.NOT_ACCEPTABLE) case class NotAcceptable[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.PROXY_AUTHENTICATION_REQUIRED) case class ProxyAuthenticationRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.REQUEST_TIMEOUT) case class RequestTimeout[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.CONFLICT) case class Conflict[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.GONE) case class Gone[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.LENGTH_REQUIRED) case class LengthRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.PRECONDITION_FAILED) case class PreconditionFailed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.REQUEST_ENTITY_TOO_LARGE) case class RequestEntityTooLarge[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.REQUEST_URI_TOO_LONG) case class RequestUriTooLong[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.UNSUPPORTED_MEDIA_TYPE) case class UnsupportedMediaType[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.REQUESTED_RANGE_NOT_SATISFIABLE) case class RequestedRangeNotSatisfiable[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.EXPECTATION_FAILED) case class ExpectationFailed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.UNPROCESSABLE_ENTITY) case class UnprocessableEntity[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.LOCKED) case class Locked[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.FAILED_DEPENDENCY) case class FailedDependency[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

@response(Status.TOO_MANY_REQUEST) case class TooManyRequest[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

// ----------------- Server Error responses -----------------

@response(Status.INTERNAL_SERVER_ERROR) case class InternalServerError[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

@response(Status.NOT_IMPLEMENTED) case class NotImplemented[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

@response(Status.BAD_GATEWAY) case class BadGateway[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

@response(Status.SERVICE_UNAVAILABLE) case class ServiceUnavailable[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

@response(Status.GATEWAY_TIMEOUT) case class GatewayTimeout[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

@response(Status.HTTP_VERSION_NOT_SUPPORTED) case class HttpVersionNotSupported[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

@response(Status.INSUFFICIENT_STORAGE) case class InsufficientStorage[+B <: ErrorBody](body: B) extends HyperbusServerException(body)