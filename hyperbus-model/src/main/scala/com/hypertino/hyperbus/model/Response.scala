package com.hypertino.hyperbus.model

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value.Value
import com.hypertino.hyperbus.model.annotations.response

trait NormalResponse extends Response[Body]

trait RedirectResponse extends Response[Body]

trait ErrorResponse extends Response[ErrorBody]

trait ServerError extends ErrorResponse

trait ClientError extends ErrorResponse

// we need this to help with code completion in IDE
trait ResponseObjectApi[PB <: Body, R <: Response[PB]] {
  def apply[B <: PB](body: B, headers: com.hypertino.hyperbus.model.Headers): R
  def apply[B <: PB](body: B)(implicit mcx: com.hypertino.hyperbus.model.MessagingContextFactory): R
  // def unapply[B <: PB](response: Response[PB]): Option[(B,Map[String,Seq[String]])] TODO: this doesn't works, find a workaround
}

// ----------------- Normal responses -----------------

@response(Status.OK) case class Ok[+B <: Body](body: B) extends NormalResponse with Response[B]

object Ok extends ResponseObjectApi[Body, Ok[Body]]

trait CreatedBody extends Body with Links {
  def location: Link = links(DefLink.LOCATION) match {
    case Left(link) ⇒ link
    case Right(sequence) ⇒ sequence.head
  }
}

@response(Status.CREATED) case class Created[+B <: CreatedBody](body: B) extends NormalResponse with Response[B]

object Created extends ResponseObjectApi[CreatedBody, Created[CreatedBody]]

object DynamicCreatedBody {
  def apply(contentType: Option[String], content: Value): DynamicBody with CreatedBody = DynamicCreatedBodyContainer(contentType, content)

  def apply(content: Value): DynamicBody with CreatedBody = apply(None, content)

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): DynamicBody with CreatedBody = {
    import com.hypertino.binders.json.JsonBinders._
    JsonBindersFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(contentType, deserializer.unbind[Value])
    }
  }

  def unapply(dynamicBody: DynamicBody with CreatedBody) = Some((dynamicBody.contentType, dynamicBody.content))
}

private[model] case class DynamicCreatedBodyContainer(contentType: Option[String], content: Value) extends DynamicBody with CreatedBody

@response(Status.ACCEPTED) case class Accepted[+B <: Body](body: B) extends NormalResponse with Response[B]

object Accepted extends ResponseObjectApi[Body, Accepted[Body]]

@response(Status.NON_AUTHORITATIVE_INFORMATION) case class NonAuthoritativeInformation[+B <: Body](body: B) extends NormalResponse with Response[B]

object NonAuthoritativeInformation extends ResponseObjectApi[Body, NonAuthoritativeInformation[Body]]

@response(Status.NO_CONTENT) case class NoContent[+B <: Body](body: B) extends NormalResponse with Response[B]

object NoContent extends ResponseObjectApi[Body, NoContent[Body]]

@response(Status.RESET_CONTENT) case class ResetContent[+B <: Body](body: B) extends NormalResponse with Response[B]

object ResetContent extends ResponseObjectApi[Body, ResetContent[Body]]

@response(Status.PARTIAL_CONTENT) case class PartialContent[+B <: Body](body: B) extends NormalResponse with Response[B]

object PartialContent extends ResponseObjectApi[Body, PartialContent[Body]]

@response(Status.MULTI_STATUS) case class MultiStatus[+B <: Body](body: B) extends NormalResponse with Response[B]

object MultiStatus extends ResponseObjectApi[Body, MultiStatus[Body]]

// ----------------- Redirect responses -----------------

// todo: URL for redirects like for created?

@response(Status.MULTIPLE_CHOICES) case class MultipleChoices[+B <: Body](body: B) extends RedirectResponse with Response[B]

object MultipleChoices extends ResponseObjectApi[Body, MultipleChoices[Body]]

@response(Status.MOVED_PERMANENTLY) case class MovedPermanently[+B <: Body](body: B) extends RedirectResponse with Response[B]

object MovedPermanently extends ResponseObjectApi[Body, MovedPermanently[Body]]

@response(Status.FOUND) case class Found[+B <: Body](body: B) extends RedirectResponse with Response[B]

object Found extends ResponseObjectApi[Body, Found[Body]]

@response(Status.SEE_OTHER) case class SeeOther[+B <: Body](body: B) extends RedirectResponse with Response[B]

object SeeOther extends ResponseObjectApi[Body, SeeOther[Body]]

@response(Status.NOT_MODIFIED) case class NotModified[+B <: Body](body: B) extends RedirectResponse with Response[B]

object NotModified extends ResponseObjectApi[Body, NotModified[Body]]

@response(Status.USE_PROXY) case class UseProxy[+B <: Body](body: B) extends RedirectResponse with Response[B]

object UseProxy extends ResponseObjectApi[Body, UseProxy[Body]]

@response(Status.TEMPORARY_REDIRECT) case class TemporaryRedirect[+B <: Body](body: B) extends RedirectResponse with Response[B]

object TemporaryRedirect extends ResponseObjectApi[Body, TemporaryRedirect[Body]]

// ----------------- Exception base classes -----------------

abstract class HyperbusException[+B <: ErrorBody](body: B)
  extends RuntimeException(body.toString) with Response[B] {
}

abstract class HyperbusServerException[+B <: ErrorBody](body: B) extends HyperbusException(body)

abstract class HyperbusClientException[+B <: ErrorBody](body: B) extends HyperbusException(body)

// ----------------- Client Error responses -----------------

@response(Status.BAD_REQUEST) case class BadRequest[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object BadRequest extends ResponseObjectApi[ErrorBody, BadRequest[ErrorBody]]

@response(Status.UNAUTHORIZED) case class Unauthorized[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Unauthorized extends ResponseObjectApi[ErrorBody, Unauthorized[ErrorBody]]

@response(Status.PAYMENT_REQUIRED) case class PaymentRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object PaymentRequired extends ResponseObjectApi[ErrorBody, PaymentRequired[ErrorBody]]

@response(Status.FORBIDDEN) case class Forbidden[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Forbidden extends ResponseObjectApi[ErrorBody, Forbidden[ErrorBody]]

@response(Status.NOT_FOUND) case class NotFound[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object NotFound extends ResponseObjectApi[ErrorBody, NotFound[ErrorBody]]

@response(Status.METHOD_NOT_ALLOWED) case class MethodNotAllowed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object MethodNotAllowed extends ResponseObjectApi[ErrorBody, MethodNotAllowed[ErrorBody]]

@response(Status.NOT_ACCEPTABLE) case class NotAcceptable[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object NotAcceptable extends ResponseObjectApi[ErrorBody, NotAcceptable[ErrorBody]]

@response(Status.PROXY_AUTHENTICATION_REQUIRED) case class ProxyAuthenticationRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object ProxyAuthenticationRequired extends ResponseObjectApi[ErrorBody, ProxyAuthenticationRequired[ErrorBody]]

@response(Status.REQUEST_TIMEOUT) case class RequestTimeout[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestTimeout extends ResponseObjectApi[ErrorBody, RequestTimeout[ErrorBody]]

@response(Status.CONFLICT) case class Conflict[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Conflict extends ResponseObjectApi[ErrorBody, Conflict[ErrorBody]]

@response(Status.GONE) case class Gone[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Gone extends ResponseObjectApi[ErrorBody, Gone[ErrorBody]]

@response(Status.LENGTH_REQUIRED) case class LengthRequired[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object LengthRequired extends ResponseObjectApi[ErrorBody, LengthRequired[ErrorBody]]

@response(Status.PRECONDITION_FAILED) case class PreconditionFailed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object PreconditionFailed extends ResponseObjectApi[ErrorBody, PreconditionFailed[ErrorBody]]

@response(Status.REQUEST_ENTITY_TOO_LARGE) case class RequestEntityTooLarge[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestEntityTooLarge extends ResponseObjectApi[ErrorBody, RequestEntityTooLarge[ErrorBody]]

@response(Status.REQUEST_URI_TOO_LONG) case class RequestUriTooLong[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestUriTooLong extends ResponseObjectApi[ErrorBody, RequestUriTooLong[ErrorBody]]

@response(Status.UNSUPPORTED_MEDIA_TYPE) case class UnsupportedMediaType[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object UnsupportedMediaType extends ResponseObjectApi[ErrorBody, UnsupportedMediaType[ErrorBody]]

@response(Status.REQUESTED_RANGE_NOT_SATISFIABLE) case class RequestedRangeNotSatisfiable[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object RequestedRangeNotSatisfiable extends ResponseObjectApi[ErrorBody, RequestedRangeNotSatisfiable[ErrorBody]]

@response(Status.EXPECTATION_FAILED) case class ExpectationFailed[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object ExpectationFailed extends ResponseObjectApi[ErrorBody, ExpectationFailed[ErrorBody]]

@response(Status.UNPROCESSABLE_ENTITY) case class UnprocessableEntity[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object UnprocessableEntity extends ResponseObjectApi[ErrorBody, UnprocessableEntity[ErrorBody]]

@response(Status.LOCKED) case class Locked[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object Locked extends ResponseObjectApi[ErrorBody, Locked[ErrorBody]]

@response(Status.FAILED_DEPENDENCY) case class FailedDependency[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object FailedDependency extends ResponseObjectApi[ErrorBody, FailedDependency[ErrorBody]]

@response(Status.TOO_MANY_REQUEST) case class TooManyRequest[+B <: ErrorBody](body: B) extends HyperbusClientException(body)

object TooManyRequest extends ResponseObjectApi[ErrorBody, TooManyRequest[ErrorBody]]

// ----------------- Server Error responses -----------------

@response(Status.INTERNAL_SERVER_ERROR) case class InternalServerError[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object InternalServerError extends ResponseObjectApi[ErrorBody, InternalServerError[ErrorBody]]

@response(Status.NOT_IMPLEMENTED) case class NotImplemented[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object NotImplemented extends ResponseObjectApi[ErrorBody, NotImplemented[ErrorBody]]

@response(Status.BAD_GATEWAY) case class BadGateway[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object BadGateway extends ResponseObjectApi[ErrorBody, BadGateway[ErrorBody]]

@response(Status.SERVICE_UNAVAILABLE) case class ServiceUnavailable[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object ServiceUnavailable extends ResponseObjectApi[ErrorBody, ServiceUnavailable[ErrorBody]]

@response(Status.GATEWAY_TIMEOUT) case class GatewayTimeout[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object GatewayTimeout extends ResponseObjectApi[ErrorBody, GatewayTimeout[ErrorBody]]

@response(Status.HTTP_VERSION_NOT_SUPPORTED) case class HttpVersionNotSupported[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object HttpVersionNotSupported extends ResponseObjectApi[ErrorBody, HttpVersionNotSupported[ErrorBody]]

@response(Status.INSUFFICIENT_STORAGE) case class InsufficientStorage[+B <: ErrorBody](body: B) extends HyperbusServerException(body)

object InsufficientStorage extends ResponseObjectApi[ErrorBody, InsufficientStorage[ErrorBody]]
