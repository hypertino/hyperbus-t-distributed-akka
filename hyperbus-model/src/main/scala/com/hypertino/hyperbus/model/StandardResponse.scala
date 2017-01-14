package com.hypertino.hyperbus.model

import com.hypertino.hyperbus.serialization._

object StandardResponse {

  def apply(responseHeader: ResponseHeader,
            responseBodyJson: com.fasterxml.jackson.core.JsonParser,
            bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body] = {
    val body =
      if (bodyDeserializer.isDefinedAt(responseHeader))
        bodyDeserializer(responseHeader)(responseHeader.contentType, responseBodyJson)
      else
        StandardResponseBody(responseHeader, responseBodyJson)
    apply(responseHeader, body)
  }

  def apply(responseHeader: ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser): Response[Body] = {
    apply(responseHeader, responseBodyJson, PartialFunction.empty)
  }

  def apply(responseHeader: ResponseHeader, body: Body): Response[Body] = {
    val headers = Headers.plain(responseHeader.headers)
    responseHeader.status match {
      case Status.OK => Ok(body, headers)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody], headers)
      case Status.ACCEPTED => Accepted(body, headers)
      case Status.NON_AUTHORITATIVE_INFORMATION => NonAuthoritativeInformation(body, headers)
      case Status.NO_CONTENT => NoContent(body, headers)
      case Status.RESET_CONTENT => ResetContent(body, headers)
      case Status.PARTIAL_CONTENT => PartialContent(body, headers)
      case Status.MULTI_STATUS => MultiStatus(body, headers)

      case Status.MULTIPLE_CHOICES => MultipleChoices(body, headers)
      case Status.MOVED_PERMANENTLY => MovedPermanently(body, headers)
      case Status.FOUND => Found(body, headers)
      case Status.SEE_OTHER => SeeOther(body, headers)
      case Status.NOT_MODIFIED => NotModified(body, headers)
      case Status.USE_PROXY => UseProxy(body, headers)
      case Status.TEMPORARY_REDIRECT => TemporaryRedirect(body, headers)

      case Status.BAD_REQUEST => BadRequest(body.asInstanceOf[ErrorBody], headers)
      case Status.UNAUTHORIZED => Unauthorized(body.asInstanceOf[ErrorBody], headers)
      case Status.PAYMENT_REQUIRED => PaymentRequired(body.asInstanceOf[ErrorBody], headers)
      case Status.FORBIDDEN => Forbidden(body.asInstanceOf[ErrorBody], headers)
      case Status.NOT_FOUND => NotFound(body.asInstanceOf[ErrorBody], headers)
      case Status.METHOD_NOT_ALLOWED => MethodNotAllowed(body.asInstanceOf[ErrorBody], headers)
      case Status.NOT_ACCEPTABLE => NotAcceptable(body.asInstanceOf[ErrorBody], headers)
      case Status.PROXY_AUTHENTICATION_REQUIRED => ProxyAuthenticationRequired(body.asInstanceOf[ErrorBody], headers)
      case Status.REQUEST_TIMEOUT => RequestTimeout(body.asInstanceOf[ErrorBody], headers)
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBody], headers)
      case Status.GONE => Gone(body.asInstanceOf[ErrorBody], headers)
      case Status.LENGTH_REQUIRED => LengthRequired(body.asInstanceOf[ErrorBody], headers)
      case Status.PRECONDITION_FAILED => PreconditionFailed(body.asInstanceOf[ErrorBody], headers)
      case Status.REQUEST_ENTITY_TOO_LARGE => RequestEntityTooLarge(body.asInstanceOf[ErrorBody], headers)
      case Status.REQUEST_URI_TOO_LONG => RequestUriTooLong(body.asInstanceOf[ErrorBody], headers)
      case Status.UNSUPPORTED_MEDIA_TYPE => UnsupportedMediaType(body.asInstanceOf[ErrorBody], headers)
      case Status.REQUESTED_RANGE_NOT_SATISFIABLE => RequestedRangeNotSatisfiable(body.asInstanceOf[ErrorBody], headers)
      case Status.EXPECTATION_FAILED => ExpectationFailed(body.asInstanceOf[ErrorBody], headers)
      case Status.UNPROCESSABLE_ENTITY => UnprocessableEntity(body.asInstanceOf[ErrorBody], headers)
      case Status.LOCKED => Locked(body.asInstanceOf[ErrorBody], headers)
      case Status.FAILED_DEPENDENCY => FailedDependency(body.asInstanceOf[ErrorBody], headers)
      case Status.TOO_MANY_REQUEST => TooManyRequest(body.asInstanceOf[ErrorBody], headers)

      case Status.INTERNAL_SERVER_ERROR => InternalServerError(body.asInstanceOf[ErrorBody], headers)
      case Status.NOT_IMPLEMENTED => NotImplemented(body.asInstanceOf[ErrorBody], headers)
      case Status.BAD_GATEWAY => BadGateway(body.asInstanceOf[ErrorBody], headers)
      case Status.SERVICE_UNAVAILABLE => ServiceUnavailable(body.asInstanceOf[ErrorBody], headers)
      case Status.GATEWAY_TIMEOUT => GatewayTimeout(body.asInstanceOf[ErrorBody], headers)
      case Status.HTTP_VERSION_NOT_SUPPORTED => HttpVersionNotSupported(body.asInstanceOf[ErrorBody], headers)
      case Status.INSUFFICIENT_STORAGE => InsufficientStorage(body.asInstanceOf[ErrorBody], headers)
    }
  }
}
