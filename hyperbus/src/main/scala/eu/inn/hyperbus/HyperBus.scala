package eu.inn.hyperbus

import java.io.InputStream

import eu.inn.hyperbus.impl.MacroApi
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization._
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros
import scala.util.Try
import scala.util.control.NonFatal

/*
todo:
+ decide group result type
+ correlationId, sequenceId, replyTo
+ other headers?
+ exception when duplicate subscription
+ encode -> serialize
+ test serialize/deserialize exceptions

low priority:
  + lostResponse response log details
*/

trait HyperBusApi {
  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDecoder: ResponseDecoder[RESP]): Future[RESP]

  def publish[REQ <: Request[Body]](request: REQ): Future[Unit]

  def process[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
                                                       method: String,
                                                       contentType: Option[String],
                                                       requestDecoder: RequestDecoder[REQ])
                                                      (handler: (REQ) => Future[RESP]): String

  def subscribe[REQ <: Request[Body]](topic: Topic,
                                      method: String,
                                      contentType: Option[String],
                                      groupName: String,
                                      requestDecoder: RequestDecoder[REQ])
                                     (handler: (REQ) => Future[Unit]): String

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

// todo: rename serviceBus!
class HyperBus(val serviceBus: TransportManager)(implicit val executionContext: ExecutionContext) extends HyperBusApi {
  protected trait Subscription[REQ <: Request[Body]] {
    def requestDecoder: RequestDecoder[REQ]
  }
  protected val subscriptions = new Subscriptions[SubKey, Subscription[_]]
  protected val underlyingSubscriptions = new mutable.HashMap[String, (String, UnderlyingHandler[_])]
  protected val log = LoggerFactory.getLogger(this.getClass)

  def <~[REQ <: Request[Body]](request: REQ): Future[Response[Body]] = macro HyperBusMacro.ask[REQ]

  def <|[REQ <: Request[Body]](request: REQ): Future[Unit] = macro HyperBusMacro.publish[REQ]

  def |>[IN <: Request[Body]](groupName: String)
                                    (handler: (IN) => Future[Unit]): String = macro HyperBusMacro.subscribe[IN]

  def ~>[REQ <: Request[Body]](handler: REQ => Future[Response[Body]]): String = macro HyperBusMacro.process[REQ]

  /*def <~(request: DynamicRequest): Future[Response[DynamicBody]] = {
    ask(request,
      macroApiImpl.responseDecoder(_,_,PartialFunction.empty)
    ).asInstanceOf[Future[Response[DynamicBody]]]
  }

  def <|(request: DynamicRequest): Future[Unit] = {
    import eu.inn.hyperbus.impl.Helpers._
    publish(request)
  }*/

  protected case class RequestReplySubscription[REQ <: Request[Body]](
                                                                       handler: (REQ) => Future[Response[Body]],
                                                                       requestDecoder: RequestDecoder[REQ]) extends Subscription[REQ]

  protected case class PubSubSubscription[REQ <: Request[Body]](
                                                                 handler: (REQ) => Future[Unit],
                                                                 requestDecoder: RequestDecoder[REQ]) extends Subscription[REQ]

  protected case class SubKey(method: String, contentType: Option[String])

  protected abstract class UnderlyingHandler[REQ <: Request[Body]](routeKey: String) {
    protected def getSubscription(in: REQ): Option[Subscription[REQ]] = getSubscription(in.method, in.body.contentType)

    protected def getSubscription(method: String, contentType: Option[String]): Option[Subscription[REQ]] = {
      subscriptions.get(routeKey).subRoutes.get(SubKey(method, contentType)).orElse {
        // at first try exact match
        subscriptions.get(routeKey).subRoutes.get(SubKey(method, None)) // at second without contentType
      } map { subscriptionList =>
        subscriptionList.getRandomSubscription.asInstanceOf[Subscription[REQ]]
      }
    }

    def decoder(inputStream: InputStream): REQ = {
      try {
        MessageDecoder.decodeRequestWith[REQ](inputStream) { (requestHeader, requestBodyJson) =>
          getSubscription(requestHeader.method, requestHeader.contentType) map { subscription =>
            subscription.requestDecoder(requestHeader, requestBodyJson)
          } getOrElse {
            DynamicRequest(requestHeader, requestBodyJson).asInstanceOf[REQ] // todo: why? remove and throw
          }
        }
      }
      catch {
        case re: ErrorResponse ⇒ throw re
        case NonFatal(e) =>
          val error = BadRequest(ErrorBody(DefError.REQUEST_PARSE_ERROR, Some(e.toString)))
          logError("Can't decode request", error)
          throw error
      }
    }
  }

  protected class UnderlyingRequestReplyHandler[REQ <: Request[Body]](routeKey: String)
    extends UnderlyingHandler[REQ](routeKey) {
    def handler(in: REQ): Future[Response[Body]] = {
      getSubscription(in) map {
        case y: RequestReplySubscription[REQ] ⇒
          val futureResult = y.handler(in)
          futureResult.recoverWith {
            case z: Response[_] ⇒ Future.successful(z)
            case t: Throwable ⇒ Future.successful(unhandledException(routeKey, in, t))
          }
      } getOrElse {
        Future.successful(unhandledRequest(routeKey, in))
      }
    }
  }

  protected class UnderlyingPubSubHandler[REQ <: Request[Body]](routeKey: String)
    extends UnderlyingHandler[REQ](routeKey) {
    def handler(in: REQ): Future[Unit] = {
      getSubscription(in).map {
        case s: PubSubSubscription[REQ] ⇒
          s.handler(in)
      } getOrElse {
        Future.successful(unhandledPublication(routeKey, in))
      }
    }
  }

  def ask[RESP <: Response[Body], REQ <: Request[Body]](request: REQ,
                                                        responseDecoder: ResponseDecoder[RESP]): Future[RESP] = {

    val outputDecoder = MessageDecoder.decodeResponseWith(_: InputStream)(responseDecoder)
    serviceBus.ask[RESP](request, outputDecoder) map {
      case throwable: Throwable ⇒ throw throwable
      case other ⇒ other
    }
  }

  def publish[REQ <: Request[Body]](request: REQ): Future[Unit] = {
    serviceBus.publish(request)
  }

  def process[RESP <: Response[Body], REQ <: Request[Body]](topic: Topic,
                                                            method: String,
                                                            contentType: Option[String],
                                                            requestDecoder: RequestDecoder[REQ])
                                                           (handler: (REQ) => Future[RESP]): String = {
    val routeKey = getRouteKey(topic.urlFilter, None)

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        RequestReplySubscription(handler.asInstanceOf[(REQ) => Future[Response[Body]]], requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingRequestReplyHandler[REQ](routeKey)
        val d: Decoder[REQ] = uh.decoder
        val uid = serviceBus.process[REQ](topic, d, exceptionEncoder)(uh.handler)
        underlyingSubscriptions += routeKey ->(uid, uh)
      }
      r
    }
  }

  def subscribe[REQ <: Request[Body]](topic: Topic,
                                      method: String,
                                      contentType: Option[String],
                                      groupName: String,
                                      requestDecoder: RequestDecoder[REQ])
                                     (handler: (REQ) => Future[Unit]): String = {
    val routeKey = getRouteKey(topic.urlFilter, Some(groupName))

    underlyingSubscriptions.synchronized {
      val r = subscriptions.add(
        routeKey,
        SubKey(method, contentType),
        PubSubSubscription(handler, requestDecoder)
      )

      if (!underlyingSubscriptions.contains(routeKey)) {
        val uh = new UnderlyingPubSubHandler[REQ](routeKey)
        val d: Decoder[REQ] = uh.decoder
        val uid = serviceBus.subscribe[REQ](topic, groupName, d)(uh.handler)
        underlyingSubscriptions += routeKey ->(uid, uh)
      }
      r
    }
  }

  def off(subscriptionId: String): Unit = {
    underlyingSubscriptions.synchronized {
      subscriptions.getRouteKeyById(subscriptionId) foreach { routeKey =>
        val cnt = subscriptions.get(routeKey).subRoutes.foldLeft(0){ (c, x) =>
          c + x._2.size
        }
        if (cnt <= 1) {
          underlyingSubscriptions.get(routeKey).foreach(k => serviceBus.off(k._1))
        }
      }
      subscriptions.remove(subscriptionId)
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    serviceBus.shutdown(duration)
  }

  protected def exceptionEncoder(exception: Throwable, outputStream: java.io.OutputStream): Unit = {
    exception match {
      case r: ErrorResponse ⇒ r.encode(outputStream)
      case t ⇒
        val error = InternalServerError(ErrorBody(DefError.INTERNAL_ERROR, Some(t.getMessage)))
        logError("Unhandled exception", error)
        error.encode(outputStream)
    }
  }

  protected def defaultResponseBodyDecoder(responseHeader: ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser): Body = {
    if (responseHeader.status >= 400 && responseHeader.status <= 599)
      ErrorBody(responseBodyJson, responseHeader.contentType)
    else
      DynamicBody(responseBodyJson, responseHeader.contentType)
  }

  val macroApiImpl = new MacroApi {
    def responseDecoder(responseHeader: ResponseHeader,
                        responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                        bodyDecoder: PartialFunction[ResponseHeader, ResponseBodyDecoder]): Response[Body] = {
      val body =
        if (bodyDecoder.isDefinedAt(responseHeader))
          bodyDecoder(responseHeader)(responseBodyJson)
        else
          defaultResponseBodyDecoder(responseHeader, responseBodyJson)
      createStandardResponse(responseHeader, body)
    }
  }

  def createStandardResponse(responseHeader: ResponseHeader, body: Body): Response[Body] = {
    val messageId = responseHeader.messageId
    val correlationId = responseHeader.correlationId.getOrElse(messageId)
    responseHeader.status match {
      case Status.OK => Ok(body, messageId, correlationId)
      case Status.CREATED => Created(body.asInstanceOf[CreatedBody], messageId, correlationId)
      case Status.ACCEPTED => Accepted(body, messageId, correlationId)
      case Status.NON_AUTHORITATIVE_INFORMATION => NonAuthoritativeInformation(body, messageId, correlationId)
      case Status.NO_CONTENT => NoContent(body, messageId, correlationId)
      case Status.RESET_CONTENT => ResetContent(body, messageId, correlationId)
      case Status.PARTIAL_CONTENT => PartialContent(body, messageId, correlationId)
      case Status.MULTI_STATUS => MultiStatus(body, messageId, correlationId)

      case Status.MULTIPLE_CHOICES => MultipleChoices(body, messageId, correlationId)
      case Status.MOVED_PERMANENTLY => MovedPermanently(body, messageId, correlationId)
      case Status.FOUND => Found(body, messageId, correlationId)
      case Status.SEE_OTHER => SeeOther(body, messageId, correlationId)
      case Status.NOT_MODIFIED => NotModified(body, messageId, correlationId)
      case Status.USE_PROXY => UseProxy(body, messageId, correlationId)
      case Status.TEMPORARY_REDIRECT => TemporaryRedirect(body, messageId, correlationId)

      case Status.BAD_REQUEST => BadRequest(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.UNAUTHORIZED => Unauthorized(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.PAYMENT_REQUIRED => PaymentRequired(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.FORBIDDEN => Forbidden(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.NOT_FOUND => NotFound(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.METHOD_NOT_ALLOWED => MethodNotAllowed(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.NOT_ACCEPTABLE => NotAcceptable(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.PROXY_AUTHENTICATION_REQUIRED => ProxyAuthenticationRequired(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.REQUEST_TIMEOUT => RequestTimeout(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.CONFLICT => Conflict(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.GONE => Gone(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.LENGTH_REQUIRED => LengthRequired(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.PRECONDITION_FAILED => PreconditionFailed(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.REQUEST_ENTITY_TOO_LARGE => RequestEntityTooLarge(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.REQUEST_URI_TOO_LONG => RequestUriTooLong(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.UNSUPPORTED_MEDIA_TYPE => UnsupportedMediaType(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.REQUESTED_RANGE_NOT_SATISFIABLE => RequestedRangeNotSatisfiable(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.EXPECTATION_FAILED => ExpectationFailed(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.UNPROCESSABLE_ENTITY => UnprocessableEntity(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.LOCKED => Locked(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.FAILED_DEPENDENCY => FailedDependency(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.TOO_MANY_REQUEST => TooManyRequest(body.asInstanceOf[ErrorBody], null, correlationId)

      case Status.INTERNAL_SERVER_ERROR => InternalServerError(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.NOT_IMPLEMENTED => NotImplemented(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.BAD_GATEWAY => BadGateway(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.SERVICE_UNAVAILABLE => ServiceUnavailable(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.GATEWAY_TIMEOUT => GatewayTimeout(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.HTTP_VERSION_NOT_SUPPORTED => HttpVersionNotSupported(body.asInstanceOf[ErrorBody], null, correlationId)
      case Status.INSUFFICIENT_STORAGE => InsufficientStorage(body.asInstanceOf[ErrorBody], null, correlationId)
    }
  }

  protected def logError(msg: String, error: HyperBusException[ErrorBody]): Unit = {
    log.error(msg + ". #" + error.body.errorId, error)
  }

  protected def safeErrorMessage(msg: String, request: Request[Body], routeKey: String): String = {
    msg + " " + safe(() => request.method) + routeKey +
      safe(() => request.body.contentType.map("@" + _).getOrElse(""))
  }

  protected def unhandledRequest(routeKey: String, request: Request[Body]): Response[Body] = {
    val s = safeErrorMessage("Unhandled request", request, routeKey)
    InternalServerError(ErrorBody(DefError.HANDLER_NOT_FOUND, Some(s)))
  }

  protected def unhandledPublication(routeKey: String, request: Request[Body]): Unit = {
    log.error(safeErrorMessage("Unhandled publication", request, routeKey))
  }

  protected def unhandledException(routeKey: String, request: Request[Body], exception: Throwable): Response[Body] = {
    val errorBody = ErrorBody(DefError.INTERNAL_ERROR, Some(
      safeErrorMessage(s"Unhandled exception: ${exception.getMessage}", request, routeKey)
    ))
    log.error(errorBody.message + ". #" + errorBody.errorId, exception)
    InternalServerError(errorBody)
  }

  protected def responseEncoderNotFound(response: Response[Body]) = log.error("Can't encode response: {}", response)

  protected def getRouteKey(urlFilter: Filter, groupName: Option[String]) = {
    val url = urlFilter.asInstanceOf[SpecificValue].value // todo: implement other filters?

    groupName.map {
      url + "#" + _
    } getOrElse url
  }

  protected def safe(t: () => String): String = Try(t()).getOrElse("???")
}
