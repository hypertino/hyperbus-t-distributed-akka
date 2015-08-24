package eu.inn.servicebus.transport.distributedakka

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

import akka.actor.{DeadLetter, Actor, ActorRef, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{Unsubscribe, Publish, SubscribeAck, Subscribe}
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{NoTransportRouteException, SubscriptionHandlerResult, Topic}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.control.NonFatal
import akka.pattern.pipe
import akka.pattern.ask


private [transport] trait Command

private [transport] case class Subscription[OUT, IN](topic: Topic,
                                                     groupName: Option[String],
                                                     inputDecoder: Decoder[IN],
                                                     partitionArgsExtractor: PartitionArgsExtractor[IN],
                                                     exceptionEncoder: Encoder[Throwable],
                                                     handler: (IN) => SubscriptionHandlerResult[OUT])

private [transport] case class Start[OUT,IN](id: String, subscription: Subscription[OUT,IN], logMessages: Boolean) extends Command

private [transport] abstract class ServerActor[OUT,IN] extends Actor {
  protected [this] val mediator = DistributedPubSubExtension(context.system).mediator
  protected [this] var subscription: Subscription[OUT,IN] = null
  protected [this] var logMessages = false
  protected [this] var log = LoggerFactory.getLogger(getClass)

  override def receive: Receive = {
    case start: Start[OUT,IN] ⇒
      subscription = start.subscription
      logMessages = start.logMessages
      mediator ! Subscribe(subscription.topic.url, Util.getUniqGroupName(subscription.groupName), self) // todo: test empty group behavior

    case ack: SubscribeAck ⇒
      context become start
  }

  override def postStop() {
    mediator ! Unsubscribe(subscription.topic.url, self)
  }

  def start: Receive

  protected def handleException(e: Throwable, sendReply: Boolean): Option[String] = {
    val msg = try {
      val outputBytes = new ByteArrayOutputStream()
      subscription.exceptionEncoder(e, outputBytes)
      Some(outputBytes.toString(Util.defaultEncoding))
    } catch {
      case NonFatal(e2) ⇒
        log.error("Can't encode exception: " + e, e2)
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
      Some(subscription.inputDecoder(inputBytes))
    }
    catch {
      case NonFatal(e) ⇒
        handleException(e, sendReply)
        None
    }
  }
}

private [transport] class ProcessServerActor[OUT,IN] extends ServerActor[OUT,IN] {
  import context._
  import eu.inn.servicebus.util.LogUtils._

  def start: Receive = {
    case input: String ⇒
      if (logMessages) {
        log.trace(Map("requestId" → input.hashCode.toHexString,
          "subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus ~> $input")
      }

      decodeMessage(input, sendReply = true) map { inputMessage ⇒
        val result = subscription.handler(inputMessage)
        val futureMessage = result.futureResult.map { out ⇒
          val outputBytes = new ByteArrayOutputStream()
          result.resultEncoder(out, outputBytes)
          outputBytes.toString(Util.defaultEncoding)
        } recover {
          case NonFatal(e) ⇒ handleException(e, sendReply = false).getOrElse(throw e) // todo: test this scenario
        }
        if (logMessages) {
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

private [transport] class SubscribeServerActor[IN] extends ServerActor[Unit,IN] {
  import context._
  import eu.inn.servicebus.util.LogUtils._
  def start: Receive = {
    case input: String ⇒
      if (logMessages) {
        log.trace(Map("subscriptionId" → subscription.handler.hashCode.toHexString), s"hyperBus |> $input")
      }
      decodeMessage(input, sendReply = false) map { inputMessage ⇒
        subscription.handler(inputMessage).futureResult.recover {
          case NonFatal(e) ⇒ log.error(Map("subscriptionId" → subscription.handler.hashCode.toHexString),
            "Subscriber handler failed", e)
        }
      }
  }
}

/*
private [transport] class NoRouteWatcher extends Actor with ActorLogging {
  import context._
  system.eventStream.subscribe(self, classOf[DeadLetter])

  override def receive: Receive = {
    case deadMessage: DeadLetter ⇒
      Future.failed(new NoTransportRouteException(deadMessage.recipient.toString())) pipeTo deadMessage.sender
  }
}
*/