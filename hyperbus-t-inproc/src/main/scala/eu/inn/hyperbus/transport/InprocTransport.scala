package eu.inn.hyperbus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.typesafe.config.Config
import eu.inn.hyperbus.model.{Body, Request}
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.inproc.{HandlerWrapper, InprocSubscription, SubKey}
import eu.inn.hyperbus.util.ConfigUtils._
import eu.inn.hyperbus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

class InprocTransport(serialize: Boolean = false)
                     (implicit val executionContext: ExecutionContext) extends ClientTransport with ServerTransport {

  def this(config: Config) = this(
    serialize = config.getOptionBoolean("serialize").getOrElse(false)
  )(
    scala.concurrent.ExecutionContext.global // todo: configurable ExecutionContext like in akka?
  )

  protected val subscriptions = new Subscriptions[SubKey, HandlerWrapper]
  protected val log = LoggerFactory.getLogger(this.getClass)

  protected def reserializeRequest[OUT <: Request[Body]](message: TransportMessage, deserializer: RequestDeserializer[OUT]): OUT = {
    if (serialize) {
      val ba = new ByteArrayOutputStream()
      message.serialize(ba)
      val bi = new ByteArrayInputStream(ba.toByteArray)
      MessageDeserializer.deserializeRequestWith(bi)(deserializer)
    }
    else {
      message.asInstanceOf[OUT]
    }
  }

  protected def reserializeResponse[OUT <: TransportResponse](message: TransportMessage, deserializer: Deserializer[OUT]): OUT = {
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
  protected def _ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse], isPublish: Boolean): Future[_] = {
    val resultPromise = Promise[TransportResponse]
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
    subscriptions.get(message.uri.pattern.specific).subRoutes.filter { subRoute ⇒
      subRoute._1.requestMatcher.matchMessage(message)
    }.foreach {
      case (subKey, subscriptionList) =>
        val subscriber = subscriptionList.getRandomSubscription
        if (subKey.groupName.isEmpty) {
          // default subscription (groupName="") returns reply

          tryX("onCommand` deserialization failed",
            reserializeRequest(message, subscriber.inputDeserializer)
          ) foreach { messageForSubscriber ⇒

            val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

            if (matched) {
              commandHandlerFound = true
              subscriber.handler(messageForSubscriber) map { case response ⇒
                if (!isPublish) {
                  val finalResponse = if (serialize) {
                    reserializeResponse(response, outputDeserializer)
                  } else {
                    response
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
            reserializeRequest(message, subscriber.inputDeserializer)
          ) foreach { messageForSubscriber ⇒

            val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

            if (matched) {
              eventHandlerFound = true
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

  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = {
    _ask(message, outputDeserializer, isPublish = false).asInstanceOf[Future[TransportResponse]]
  }

  override def publish(message: TransportRequest): Future[PublishResult] = {
    _ask(message, null, isPublish = true).asInstanceOf[Future[PublishResult]]
  }

  override def onCommand(matcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[Request[Body]])
                        (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription] = {

    if (matcher.uri.isEmpty)
      throw new IllegalArgumentException("requestMatcher.uri is empty")

    val id = subscriptions.add(
      matcher.uri.get.pattern.specific, // currently only Specific url's are supported, todo: add Regex, Any, etc...
      SubKey(None, matcher),
      HandlerWrapper(inputDeserializer, handler)
    )
    Future.successful(InprocSubscription(id))
  }

  override def onEvent(matcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[Request[Body]])
                      (handler: (Request[Body]) => Future[Unit]): Future[Subscription] = {
    if (matcher.uri.isEmpty)
      throw new IllegalArgumentException("requestMatcher.uri")

    val id = subscriptions.add(
      matcher.uri.get.pattern.specific, // currently only Specific url's are supported, todo: add Regex, Any, etc...
      SubKey(Some(groupName), matcher),
      HandlerWrapper(inputDeserializer, handler.asInstanceOf[(TransportRequest) => Future[TransportResponse]])
    )
    Future.successful(InprocSubscription(id))
  }

  override def off(subscription: Subscription): Future[Unit] = {
    subscription match {
      case i: InprocSubscription ⇒
        Future {
          subscriptions.remove(i.id)
        }
      case other ⇒
        Future.failed {
          new ClassCastException(s"InprocSubscription expected but ${other.getClass} is received")
        }
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    subscriptions.clear()
    Future.successful(true)
  }
}
