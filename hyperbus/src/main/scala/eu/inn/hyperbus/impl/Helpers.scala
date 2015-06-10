package eu.inn.hyperbus.impl

import java.io.OutputStream

import com.fasterxml.jackson.core.JsonParser
import eu.inn.binders.dynamic.Value
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.impl
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.serialization.impl.InnerHelpers
import eu.inn.hyperbus.serialization.{DecodeException, RequestHeader, ResponseHeader}
import eu.inn.servicebus.serialization.Encoder
import eu.inn.servicebus.transport.{ExactArg, AnyArg, PartitionArgs, Topic}

import scala.collection.mutable

object Helpers {
  def extractParametersFromUrl(url: String): Seq[String] = {
    val DEFAULT = 0
    val ARG = 1
    var state = DEFAULT
    val result = new mutable.MutableList[String]
    val buf = new mutable.StringBuilder

    url.foreach { c ⇒
      state match {
        case DEFAULT ⇒
          c match {
            case '{' ⇒
              state = ARG
            case _ ⇒
          }
        case ARG ⇒
          c match {
            case '}' ⇒
              result += buf.toString()
              buf.clear()
              state = DEFAULT
            case _ ⇒
              buf += c
          }
      }
    }

    result.toSeq
  }

  def topicWithAllPartitions(url: String): Topic = Topic(url, PartitionArgs(extractParametersFromUrl(url).map(_ → AnyArg).toMap))

  // todo: Generic Errors and Responses
  // handle non-standrard status
  def createResponse(responseHeader: ResponseHeader, body: Body): Response[Body] = {
    responseHeader.status match {
      case Status.OK => Ok(body)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody])
      case Status.ACCEPTED => Accepted(body)
      case Status.NON_AUTHORITATIVE_INFORMATION => NonAuthoritativeInformation(body)
      case Status.NO_CONTENT => NoContent(body)
      case Status.RESET_CONTENT => ResetContent(body)
      case Status.PARTIAL_CONTENT => PartialContent(body)
      case Status.MULTI_STATUS => MultiStatus(body)

      case Status.MULTIPLE_CHOICES => MultipleChoices(body)
      case Status.MOVED_PERMANENTLY => MovedPermanently(body)
      case Status.FOUND => Found(body)
      case Status.SEE_OTHER => SeeOther(body)
      case Status.NOT_MODIFIED => NotModified(body)
      case Status.USE_PROXY => UseProxy(body)
      case Status.TEMPORARY_REDIRECT => TemporaryRedirect(body)

      case Status.BAD_REQUEST => BadRequest(body.asInstanceOf[ErrorBody])
      case Status.UNAUTHORIZED => Unauthorized(body.asInstanceOf[ErrorBody])
      case Status.PAYMENT_REQUIRED => PaymentRequired(body.asInstanceOf[ErrorBody])
      case Status.FORBIDDEN => Forbidden(body.asInstanceOf[ErrorBody])
      case Status.NOT_FOUND => NotFound(body.asInstanceOf[ErrorBody])
      case Status.METHOD_NOT_ALLOWED => MethodNotAllowed(body.asInstanceOf[ErrorBody])
      case Status.NOT_ACCEPTABLE => NotAcceptable(body.asInstanceOf[ErrorBody])
      case Status.PROXY_AUTHENTICATION_REQUIRED => ProxyAuthenticationRequired(body.asInstanceOf[ErrorBody])
      case Status.REQUEST_TIMEOUT => RequestTimeout(body.asInstanceOf[ErrorBody])
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBody])
      case Status.GONE => Gone(body.asInstanceOf[ErrorBody])
      case Status.LENGTH_REQUIRED => LengthRequired(body.asInstanceOf[ErrorBody])
      case Status.PRECONDITION_FAILED => PreconditionFailed(body.asInstanceOf[ErrorBody])
      case Status.REQUEST_ENTITY_TOO_LARGE => RequestEntityTooLarge(body.asInstanceOf[ErrorBody])
      case Status.REQUEST_URI_TOO_LONG => RequestUriTooLong(body.asInstanceOf[ErrorBody])
      case Status.UNSUPPORTED_MEDIA_TYPE => UnsupportedMediaType(body.asInstanceOf[ErrorBody])
      case Status.REQUESTED_RANGE_NOT_SATISFIABLE => RequestedRangeNotSatisfiable(body.asInstanceOf[ErrorBody])
      case Status.EXPECTATION_FAILED => ExpectationFailed(body.asInstanceOf[ErrorBody])
      case Status.UNPROCESSABLE_ENTITY => UnprocessableEntity(body.asInstanceOf[ErrorBody])
      case Status.LOCKED => Locked(body.asInstanceOf[ErrorBody])
      case Status.FAILED_DEPENDENCY => FailedDependency(body.asInstanceOf[ErrorBody])
      case Status.TOO_MANY_REQUEST => TooManyRequest(body.asInstanceOf[ErrorBody])

      case Status.INTERNAL_SERVER_ERROR => InternalServerError(body.asInstanceOf[ErrorBody])
      case Status.NOT_IMPLEMENTED => NotImplemented(body.asInstanceOf[ErrorBody])
      case Status.BAD_GATEWAY => BadGateway(body.asInstanceOf[ErrorBody])
      case Status.SERVICE_UNAVAILABLE => ServiceUnavailable(body.asInstanceOf[ErrorBody])
      case Status.GATEWAY_TIMEOUT => GatewayTimeout(body.asInstanceOf[ErrorBody])
      case Status.HTTP_VERSION_NOT_SUPPORTED => HttpVersionNotSupported(body.asInstanceOf[ErrorBody])
      case Status.INSUFFICIENT_STORAGE => InsufficientStorage(body.asInstanceOf[ErrorBody])
    }
  }

  def decodeDynamicRequest(requestHeader: RequestHeader, jsonParser: JsonParser): DynamicRequest = {
    val body = SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      DynamicBody(deserializer.unbind[Value], requestHeader.contentType)
    }
    requestHeader.method match {
      case Method.GET => DynamicGet(requestHeader.url, body)
      case Method.POST => DynamicPost(requestHeader.url, body)
      case Method.PUT => DynamicPut(requestHeader.url, body)
      case Method.DELETE => DynamicDelete(requestHeader.url, body)
      case Method.PATCH => DynamicPatch(requestHeader.url, body)
      case _ => throw new DecodeException(s"Unknown method: '${requestHeader.method}'") //todo: save more details (messageId) or introduce DynamicMethodRequest
    }
  }

  def encodeDynamicRequest(request: DynamicRequest, outputStream: OutputStream): Unit = {
    val bodyEncoder: Encoder[DynamicBody] = dynamicBodyEncoder
    InnerHelpers.encodeMessage(request, bodyEncoder, outputStream)
  }

  def extractDynamicPartitionArgs(request: DynamicRequest) = PartitionArgs(
    impl.Helpers.extractParametersFromUrl(request.url).map { arg ⇒
      arg → ExactArg(request.body.content.asMap.get(arg).map(_.asString) getOrElse "") // todo: inner fields like abc.userId
    }.toMap
  )

  def dynamicBodyEncoder(body: DynamicBody, outputStream: OutputStream): Unit = {
    dynamicValueEncoder(body.content, outputStream)
  }

  def dynamicValueEncoder(value: Value, outputStream: OutputStream): Unit = {
    import eu.inn.hyperbus.serialization.impl.InnerHelpers.bindOptions
    eu.inn.servicebus.serialization.createEncoder[Value](value, outputStream)
  }
}
