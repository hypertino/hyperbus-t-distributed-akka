package eu.inn.servicebus

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.serialization.{Decoder, Encoder, PartitionArgsExtractor}
import eu.inn.servicebus.transport._
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

//import scala.language.experimental.macros

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

  def process[OUT, IN](topic: Topic, inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: Topic, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String

  def off(subscriptionId: String): Unit

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

case class TransportRoute[T](transport: T, urlArg: PartitionArg, partitionArgs: PartitionArgs = PartitionArgs(Map.empty))

case class ServiceBusConfiguration(clientRoutes: Seq[TransportRoute[ClientTransport]],
                                   serverRoutes: Seq[TransportRoute[ServerTransport]])

class ServiceBus(val clientRoutes: Seq[TransportRoute[ClientTransport]],
                 val serverRoutes: Seq[TransportRoute[ServerTransport]],
                 implicit val executionContext: ExecutionContext = ExecutionContext.global) extends ServiceBusApi {

  def this(configuration: ServiceBusConfiguration) = this(configuration.clientRoutes,
    configuration.serverRoutes, ExecutionContext.global)

  protected val subscriptions = new TrieMap[String, (Topic, String)]
  protected val idCounter = new AtomicLong(0)
  protected val log = LoggerFactory.getLogger(this.getClass)

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

  def process[OUT, IN](topic: Topic,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    val underlyingSubscriptionId = lookupServerTransport(topic).process[OUT, IN](
      topic,
      inputDecoder,
      partitionArgsExtractor,
      exceptionEncoder)(handler)

    val result = addSubscriptionLink(topic, underlyingSubscriptionId)
    log.info(s"New processor on $topic: #${handler.hashCode}. Id = $result")
    result
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
    val result = addSubscriptionLink(topic, underlyingSubscriptionId)
    log.info(s"New subscription on $topic($groupName): #${handler.hashCode}. Id = $result")
    result
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    val client = Future.sequence(serverRoutes.map(_.transport.shutdown(duration)))
    val server = Future.sequence(clientRoutes.map(_.transport.shutdown(duration)))
    client flatMap { c ⇒
      server map { s ⇒
        s.forall(_ == true) && c.forall(_ == true)
      }
    }
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
