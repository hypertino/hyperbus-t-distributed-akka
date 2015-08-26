package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.serialization._
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
 * Manages transport layer based on provided route configuration.
 *
 * @param clientRoutes - routes clients/consumer calls to specific transport
 * @param serverRoutes - routes messages from specific transport to server/producer subscribed on topic
 * @param executionContext - execution context used by transport layer
 */
class TransportManager(protected [this] val clientRoutes: Seq[TransportRoute[ClientTransport]],
                       protected [this] val serverRoutes: Seq[TransportRoute[ServerTransport]],
                       implicit protected [this] val executionContext: ExecutionContext) extends TransportManagerApi {

  protected [this] val subscriptions = new TrieMap[String, (Topic, String)]
  protected [this] val idCounter = new AtomicLong(0)
  protected [this] val log = LoggerFactory.getLogger(this.getClass)

  def this(configuration: ServiceBusConfiguration) = this(configuration.clientRoutes,
    configuration.serverRoutes, ExecutionContext.global)

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

  protected def lookupClientTransport(topic: Topic): ClientTransport = lookupTransport(topic, clientRoutes)

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

  protected def addSubscriptionLink(topic: Topic, underlyingSubscriptionId: String) = {
    val subscriptionId = idCounter.incrementAndGet().toHexString
    subscriptions.put(subscriptionId, (topic, underlyingSubscriptionId))
    subscriptionId
  }

  protected def lookupServerTransport(topic: Topic): ServerTransport = lookupTransport(topic, serverRoutes)

  protected def lookupTransport[T](topic: Topic, routes: Seq[TransportRoute[T]]): T = {
    routes.find(r ⇒ r.urlArg.matchArg(ExactArg(topic.url)) &&
      r.partitionArgs.matchArgs(topic.partitionArgs)) map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"Topic: ${topic.url}/${topic.partitionArgs.toString}")
    }
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
}
