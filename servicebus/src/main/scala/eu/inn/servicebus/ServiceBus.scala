package eu.inn.servicebus

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.serialization.{PartitionArgsExtractor, Decoder, Encoder}
import eu.inn.servicebus.transport._
import eu.inn.servicebus.util.{SubscriptionKey, Subscriptions}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.experimental.macros

trait ServiceBusBase {
  def ask[OUT,IN](
                    topic: Topic,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[PublishResult]

  def on[OUT,IN](topic: Topic, inputDecoder: Decoder[IN],
                 partitionArgsExtractor: PartitionArgsExtractor[IN])
                (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: Topic, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                    (handler: (IN) => SubscriptionHandlerResult[Unit]): String

  def off(subscriptionId: String): Unit
}

class ServiceBus(val defaultClientTransport: ClientTransport, val defaultServerTransport: ServerTransport)
  extends ServiceBusBase {

  protected val clientRoutes = new TrieMap[String, ClientTransport]
  protected val serverRoutes = new TrieMap[String, ServerTransport]
  protected val subscriptions = new TrieMap[String, (Topic, String)]
  protected val idCounter = new AtomicLong(0)

  def ask[OUT,IN](
                    topicUrl: String,
                    message: IN
                    ): Future[OUT] = macro ServiceBusMacro.ask[OUT,IN]

  def publish[IN](
                   topicUrl: String,
                   message: IN
                   ): Future[PublishResult] = macro ServiceBusMacro.publish[IN]

  def ask[OUT,IN](
                    topic: Topic,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT] = {
    this.lookupClientTransport(topic).ask[OUT,IN](topic,message,inputEncoder,outputDecoder)
  }

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[PublishResult] = {
    this.lookupClientTransport(topic).publish[IN](topic,message,inputEncoder)
  }

  def on[OUT,IN](topicUrl: String)
                (handler: (IN) => Future[OUT]): String = macro ServiceBusMacro.on[OUT,IN]

  def subscribe[IN](topicUrl: String, groupName: String)
                (handler: (IN) => Future[Unit]): String = macro ServiceBusMacro.subscribe[IN]

  def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach(s ⇒ lookupServerTransport(s._1).off(s._2))
    subscriptions.remove(subscriptionId)
  }

  def on[OUT,IN](topic: Topic,
                 inputDecoder: Decoder[IN],
                 partitionArgsExtractor: PartitionArgsExtractor[IN])
                (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    val underlyingSubscriptionId = lookupServerTransport(topic).on[OUT,IN](topic, inputDecoder)(handler)
    addSubscriptionLink(topic, underlyingSubscriptionId)
  }

  def subscribe[IN](topic: Topic,
                    groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String = {
    val underlyingSubscriptionId = lookupServerTransport(topic).subscribe[IN](topic, groupName, inputDecoder)(handler)
    addSubscriptionLink(topic, underlyingSubscriptionId)
  }

  protected def addSubscriptionLink(topic: Topic, underlyingSubscriptionId: String) = {
    val subscriptionId = idCounter.incrementAndGet().toHexString
    subscriptions.put(subscriptionId, (topic, underlyingSubscriptionId))
    subscriptionId
  }
  protected def lookupServerTransport(topic: Topic): ServerTransport =
    serverRoutes.getOrElse(topic.url, defaultServerTransport)

  protected def lookupClientTransport(topic: Topic): ClientTransport =
    clientRoutes.getOrElse(topic.url, defaultClientTransport)
}
