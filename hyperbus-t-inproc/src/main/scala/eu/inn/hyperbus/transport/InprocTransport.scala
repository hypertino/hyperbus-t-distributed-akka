package eu.inn.hyperbus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.typesafe.config.Config
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher
import eu.inn.hyperbus.transport.inproc.{SubKey, Subscription}
import eu.inn.hyperbus.util.ConfigUtils._
import eu.inn.hyperbus.util.{TransportUtils, Subscriptions}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.control.NonFatal

class InprocTransport(serialize: Boolean = false,
                      val logMessages: Boolean = false)
                     (implicit val executionContext: ExecutionContext) extends ClientTransport with ServerTransport with TransportUtils {

  def this(config: Config) = this(
    serialize = config.getOptionBoolean("serialize").getOrElse(false),
    logMessages = config.getOptionBoolean("log-messages") getOrElse false
  )(
    scala.concurrent.ExecutionContext.global // todo: configurable ExecutionContext like in akka?
  )

  protected val subscriptions = new Subscriptions[SubKey, Subscription]
  protected val log = LoggerFactory.getLogger(this.getClass)

  protected def reserializeMessage[OUT <: TransportMessage](message: TransportMessage, deserializer: Deserializer[OUT]): OUT = {
    if (serialize) {
      val ba = new ByteArrayOutputStream()
      message.serialize(ba)
      val bi = new ByteArrayInputStream(ba.toByteArray)
      deserializer(bi)
    }
    else {
      message.asInstanceOf[OUT]
    }
  }

  // todo: refactor this method, it's awful
  protected def _ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT], isPublish: Boolean): Future[_] = {
    val resultPromise = Promise[OUT]
    var commandHandlerFound = false
    var eventHandlerFound = false
    //var result: Future[OUT] = null

    def tryX[T](failMsg: String, code: ⇒ T): Option[T] = {
      try {
        Some(code)
      }
      catch {
        case NonFatal(e) ⇒
          log.error(failMsg, e)
          None
      }
    }

    // todo: filter is redundant for inproc?
    subscriptions.get(message.uri.pattern.specific).subRoutes.filter{subRoute ⇒
      subRoute._1.requestMatcher.matchMessage(message)
    }.foreach {
      case (subKey, subscriptionList) =>
        val subscriber = subscriptionList.getRandomSubscription
        if (subKey.groupName.isEmpty) {
          // default subscription (groupName="") returns reply

          tryX("onCommand` deserialization failed",
            reserializeMessage(message, subscriber.inputDeserializer)
          ) foreach { messageForSubscriber ⇒

            val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

            // todo: log if not matched?
            if (matched) {
              commandHandlerFound = true
              logCommandRequest(messageForSubscriber, subKey.hashCode.toHexString)
              subscriber.handler(messageForSubscriber) map { case response ⇒
                logCommandResponse(response, subKey.hashCode.toHexString)
                if (!isPublish) {
                  val finalResponse = if (serialize) {
                    reserializeMessage(response, outputDeserializer)
                  } else {
                    response.asInstanceOf[OUT]
                  }
                  resultPromise.success(finalResponse)
                }
              } recover {
                case NonFatal(e) ⇒
                  log.error("`onCommand` handler failed with", e)
              }
            }
            else {
              log.error(s"Message ($messageForSubscriber) is not matched after serialize and deserialize")
            }
          }
        } else {
          tryX("onEvent` deserialization failed",
            reserializeMessage(message, subscriber.inputDeserializer)
          ) foreach { messageForSubscriber ⇒

            val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

            if (matched) {
              eventHandlerFound = true
              logEventMessage(message, subKey.hashCode.toHexString)
              subscriber.handler(messageForSubscriber).onFailure {
                case NonFatal(e) ⇒
                  log.error("`onEvent` handler failed with", e)
              }
            }
            else {
              log.error(s"Message ($messageForSubscriber) is not matched after serialize and deserialize")
            }
          }
        }
    }

    if (!commandHandlerFound && !eventHandlerFound) {
      Future.failed {
        new NoTransportRouteException(s"Handler is not found for ${message.uri} with header matchers: ${message.headers}")
      }
    }
    else if (isPublish) {
      Future.successful {
        new PublishResult {
          def sent = Some(true)
          def offset = None
          override def toString = s"PublishResult(sent=Some(true),offset=None)"
        }
      }
    } else {
      resultPromise.future
    }
  }

  override def ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT]): Future[OUT] = {
    logAskRequest(message)
    _ask(message, outputDeserializer, isPublish = false).asInstanceOf[Future[OUT]]
  }

  override def publish(message: TransportRequest): Future[PublishResult] = {
    logPublishMessage(message)
    _ask[TransportResponse](message, null, isPublish = true).asInstanceOf[Future[PublishResult]]
  }

  override def onCommand[IN <: TransportRequest](requestMatcher: TransportRequestMatcher,
                                                 inputDeserializer: Deserializer[IN])
                                                (handler: (IN) => Future[TransportResponse]): String = {

    if (requestMatcher.uri.isEmpty)
      throw new IllegalArgumentException("requestMatcher.uri is empty")

    subscriptions.add(
      requestMatcher.uri.get.pattern.specific, // currently only Specific url's are supported, todo: add Regex, Any, etc...
      SubKey(None, requestMatcher),
      Subscription(inputDeserializer, handler.asInstanceOf[(TransportRequest) => Future[TransportResponse]])
    )
  }

  override def onEvent[IN <: TransportRequest](requestMatcher: TransportRequestMatcher,
                                               groupName: String,
                                               inputDeserializer: Deserializer[IN])
                                              (handler: (IN) => Future[Unit]): String = {
    if (requestMatcher.uri.isEmpty)
      throw new IllegalArgumentException("requestMatcher.uri")

    subscriptions.add(
      requestMatcher.uri.get.pattern.specific, // currently only Specific url's are supported, todo: add Regex, Any, etc...
      SubKey(Some(groupName), requestMatcher),
      Subscription(inputDeserializer, handler.asInstanceOf[(TransportRequest) => Future[TransportResponse]])
    )
  }

  override def off(subscriptionId: String) = {
    subscriptions.remove(subscriptionId)
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    subscriptions.clear()
    Future.successful(true)
  }
}
