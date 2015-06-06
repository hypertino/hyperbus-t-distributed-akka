package eu.inn.servicebus.transport.distributedakka

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

import akka.actor.{ActorLogging, Actor}
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.{SubscribeAck, Subscribe}
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{Util, SubscriptionHandlerResult, Topic}

private [transport] trait Command

private [transport] case class Subscription[OUT, IN](topic: Topic,
                                                     groupName: Option[String],
                                                     inputDecoder: Decoder[IN],
                                                     partitionArgsExtractor: PartitionArgsExtractor[IN],
                                                     handler: (IN) => SubscriptionHandlerResult[OUT])

private [transport] case class Start[OUT,IN](id: String, subscription: Subscription[OUT,IN]) extends Command

private [transport] case object StopServer extends Command

private [transport] abstract class ServerActor[OUT,IN] extends Actor with ActorLogging {
  protected [this] val mediator = DistributedPubSubExtension(context.system).mediator
  protected [this] var subscription: Subscription[OUT,IN] = null

  override def receive: Receive = {
    case start: Start[OUT,IN] ⇒
      subscription = start.subscription
      mediator ! Subscribe(subscription.topic.url, Util.getUniqGroupName(subscription.groupName), self) // todo: test empty group behavior

    case ack: SubscribeAck ⇒
      context become start
  }

  def start: Receive
}

private [transport] class OnServerActor[OUT,IN] extends ServerActor[OUT,IN] {
  import context._
  import akka.pattern.pipe

  def start: Receive = {
    case input: String ⇒
      val inputBytes = new ByteArrayInputStream(input.getBytes(Util.defaultEncoding))
      val inputMessage = subscription.inputDecoder(inputBytes)
      val result = subscription.handler(inputMessage)
      val futureMessage = result.futureResult.map { out ⇒
        val outputBytes = new ByteArrayOutputStream()
        result.resultEncoder(out, outputBytes)
        outputBytes.toString(Util.defaultEncoding)
      }
      futureMessage pipeTo sender
  }
}

private [transport] class SubscribeServerActor[IN] extends ServerActor[Unit,IN] {
  def start: Receive = {
    case input: String ⇒
      val inputBytes = new ByteArrayInputStream(input.getBytes(Util.defaultEncoding))
      val inputMessage = subscription.inputDecoder(inputBytes)
      subscription.handler(inputMessage)
  }
}