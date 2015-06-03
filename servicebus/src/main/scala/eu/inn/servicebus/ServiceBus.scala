package eu.inn.servicebus

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.serialization.{Decoder, Encoder, PartitionArgsExtractor}
import eu.inn.servicebus.transport._

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.experimental.macros

trait ServiceBusApi {
  def ask[OUT, IN](
                    topic: Topic,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[Unit]

  def on[OUT, IN](topic: Topic, inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: Topic, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String

  def off(subscriptionId: String): Unit
}

case class TransportRoute[T](transport: T, urlArg: PartitionArg, partitionArgs: PartitionArgs = PartitionArgs(Map()))

case class ServiceBusConfiguration(clientRoutes: Seq[TransportRoute[ClientTransport]],
                                   serverRoutes: Seq[TransportRoute[ServerTransport]])

class ServiceBus(val clientRoutes: Seq[TransportRoute[ClientTransport]],
                 val serverRoutes: Seq[TransportRoute[ServerTransport]]) extends ServiceBusApi {

  def this(configuration: ServiceBusConfiguration) = this(configuration.clientRoutes, configuration.serverRoutes)

  protected val subscriptions = new TrieMap[String, (Topic, String)]
  protected val idCounter = new AtomicLong(0)

  def ask[OUT, IN](
                    topic: Topic,
                    message: IN
                    ): Future[OUT] = macro ServiceBusMacro.ask[OUT, IN]

  def publish[IN](
                   topic: Topic,
                   message: IN
                   ): Future[Unit] = macro ServiceBusMacro.publish[IN]

  def on[OUT, IN](topic: Topic, partitionArgsExtractor: PartitionArgsExtractor[IN])
                 (handler: (IN) => Future[OUT]): String = macro ServiceBusMacro.on[OUT, IN]

  def subscribe[IN](topic: Topic, groupName: String, partitionArgsExtractor: PartitionArgsExtractor[IN])
            (handler: (IN) => Future[Unit]): String = macro ServiceBusMacro.subscribe[IN]

  def ask[OUT, IN](
                    topic: Topic,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT] = {
    this.lookupClientTransport(topic).ask[OUT, IN](topic, message, inputEncoder, outputDecoder)
  }

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[Unit] = {
    this.lookupClientTransport(topic).publish[IN](topic, message, inputEncoder)
  }

  def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach(s ⇒ lookupServerTransport(s._1).off(s._2))
    subscriptions.remove(subscriptionId)
  }

  def on[OUT, IN](topic: Topic,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    val underlyingSubscriptionId = lookupServerTransport(topic).on[OUT, IN](
      topic,
      inputDecoder,
      partitionArgsExtractor)(handler)

    addSubscriptionLink(topic, underlyingSubscriptionId)
  }

  def subscribe[IN](topic: Topic,
                    groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String = {
    val underlyingSubscriptionId = lookupServerTransport(topic).subscribe[IN](
      topic,
      groupName,
      inputDecoder,
      partitionArgsExtractor)(handler)
    addSubscriptionLink(topic, underlyingSubscriptionId)
  }

  protected def addSubscriptionLink(topic: Topic, underlyingSubscriptionId: String) = {
    val subscriptionId = idCounter.incrementAndGet().toHexString
    subscriptions.put(subscriptionId, (topic, underlyingSubscriptionId))
    subscriptionId
  }

  protected def lookupServerTransport(topic: Topic): ServerTransport = lookupTransport(topic, serverRoutes)

  protected def lookupClientTransport(topic: Topic): ClientTransport = lookupTransport(topic, clientRoutes)

  protected def lookupTransport[T](topic: Topic, routes: Seq[TransportRoute[T]]) : T = {
    routes.find(r ⇒ r.urlArg.matchArg(ExactArg(topic.url)) &&
      r.partitionArgs.matchArgs(topic.partitionArgs)) map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"Topic: ${topic.url}/${topic.partitionArgs.toString}")
    }
  }
}
