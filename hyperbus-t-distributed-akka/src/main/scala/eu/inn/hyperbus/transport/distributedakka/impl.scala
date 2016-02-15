package eu.inn.hyperbus.transport.distributedakka

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.{Actor, DeadLetter}
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe, UnsubscribeAck}
import akka.pattern.pipe
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.control.NonFatal

private[transport] trait Command

private[transport] case class Subscription[OUT, IN <: TransportRequest](uri: Uri,
                                                                        groupName: Option[String],
                                                                        inputDeserializer: Deserializer[IN],
                                                                        exceptionSerializer: Serializer[Throwable],
                                                                        handler: (IN) => Future[OUT]) {
  def topic = uri.pattern.specific // currently only Specific url's are supported, todo: add Regex, Any, etc...
}

private[transport] case class Start[OUT, IN <: TransportRequest](id: String, subscription: Subscription[OUT, IN], logMessages: Boolean) extends Command

private [transport] object Stop

private[transport] abstract class ServerActor[OUT, IN <: TransportRequest] extends Actor {
  protected[this] val mediator = DistributedPubSubEx(context.system).mediator
  protected[this] var subscription: Subscription[OUT, IN] = null
  protected[this] var logMessages = false
  protected[this] var log = LoggerFactory.getLogger(getClass)

  override def receive: Receive = handleStart orElse handleStop

  def handleStart: Receive = {
    case start: Start[OUT, IN] ⇒
      subscription = start.subscription
      logMessages = start.logMessages
      log.debug(s"$self is subscribing to topic ${subscription.topic}/${subscription.groupName}")
      mediator ! Subscribe(subscription.uri.pattern.specific, Util.getUniqGroupName(subscription.groupName), self) // todo: test empty group behavior

    case ack: SubscribeAck ⇒
      log.debug(s"$self is subscribed to topic ${subscription.topic}/${subscription.groupName}")
      context become (start orElse handleStop)
  }

  def handleStop: Receive = {
    case Stop ⇒
      log.debug(s"$self is unsubscribing from topic ${subscription.topic}/${subscription.groupName}")
      mediator ! Unsubscribe(subscription.topic, self)

    case UnsubscribeAck(unsubscribe) ⇒
      log.debug(s"$self is stopping...")
      context.stop(self)
  }

  def start: Receive

  protected def handleException(e: Throwable, sendReply: Boolean): Option[String] = {
    val msg = try {
      val outputBytes = new ByteArrayOutputStream()
      subscription.exceptionSerializer(e, outputBytes)
      Some(outputBytes.toString(Util.defaultEncoding))
    } catch {
      case NonFatal(e2) ⇒
        log.error("Can't serialize exception: " + e, e2)
        None
    }

    if (sendReply) {
      msg.foreach { s ⇒
        import context._
        Future.successful(s) pipeTo context.sender
      }
    }

    msg
  }

  protected def decodeMessage(input: String, sendReply: Boolean) = {
    try {
      val inputBytes = new ByteArrayInputStream(input.getBytes(Util.defaultEncoding))
      Some(subscription.inputDeserializer(inputBytes))
    }
    catch {
      case NonFatal(e) ⇒
        handleException(e, sendReply)
        None
    }
  }
}

private[transport] class ProcessServerActor[IN <: TransportRequest] extends ServerActor[TransportResponse, IN] {

  import context._
  import eu.inn.hyperbus.util.LogUtils._

  def start: Receive = {
    case input: String ⇒
      if (logMessages && log.isTraceEnabled) {
        log.trace(Map("requestId" → input.hashCode.toHexString,
          "subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus ~> $input")
      }

      decodeMessage(input, sendReply = true) map { inputMessage ⇒
        val result = subscription.handler(inputMessage) // todo: test result with partitonArgs?
      val futureMessage = result.map { out ⇒
          val outputBytes = new ByteArrayOutputStream()
          out.serialize(outputBytes)
          outputBytes.toString(Util.defaultEncoding)
        } recover {
          case NonFatal(e) ⇒ handleException(e, sendReply = false).getOrElse(throw e) // todo: test this scenario
        }
        if (logMessages && log.isTraceEnabled) {
          futureMessage map { s ⇒
            log.trace(Map("requestId" → input.hashCode.toHexString,
              "subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus <~(R)~ $s")
            s
          } pipeTo sender
        }
        else {
          futureMessage pipeTo sender
        }
      }
  }
}

private[transport] class SubscribeServerActor[IN <: TransportRequest] extends ServerActor[Unit, IN] {

  import context._
  import eu.inn.hyperbus.util.LogUtils._

  def start: Receive = {
    case input: String ⇒
      if (logMessages && log.isTraceEnabled) {
        log.trace(Map("subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus |> $input")
      }
      decodeMessage(input, sendReply = false) map { inputMessage ⇒
        subscription.handler(inputMessage).recover {
          // todo: test result with partitonArgs?
          case NonFatal(e) ⇒ log.error(Map("subscriptionId" → subscription.handler.hashCode.toHexString),
            "Subscriber handler failed", e)
        }
      }
  }
}

private [transport] class NoRouteWatcher extends Actor {
  import context._
  system.eventStream.subscribe(self, classOf[DeadLetter])

  override def receive: Receive = {
    case deadMessage: DeadLetter ⇒
      // hyperbus messages are strings, this is dirty hack, todo: fix
      if (deadMessage.message.isInstanceOf[String]) {
        Future.failed(new NoTransportRouteException(deadMessage.recipient.toString())) pipeTo deadMessage.sender
      }
  }
}
