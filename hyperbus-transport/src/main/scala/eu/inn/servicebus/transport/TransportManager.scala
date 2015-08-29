package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.config.TransportConfiguration
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

  def this(configuration: TransportConfiguration) = this(configuration.clientRoutes,
    configuration.serverRoutes, ExecutionContext.global)

  def ask[OUT <: TransportResponse](message: TransportRequest,outputDecoder: Decoder[OUT]): Future[OUT] = {
    this.lookupClientTransport(message.topic).ask[OUT](message, outputDecoder)
  }

  def publish(message: TransportRequest): Future[Unit] = {
    this.lookupClientTransport(message.topic).publish(message)
  }

  protected def lookupClientTransport(topic: Topic): ClientTransport = {
    clientRoutes.find(r ⇒ r.urlArg.matchFilter(topic.urlFilter) &&
      r.valueFilters.matchFilters(topic.valueFilters)) map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"Topic: $topic")
    }
  }

  def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach(s ⇒ lookupServerTransport(s._1).off(s._2))
    subscriptions.remove(subscriptionId)
  }

  def process[IN <: TransportRequest](topicFilter: Topic,
                                      inputDecoder: Decoder[IN],
                                      exceptionEncoder: Encoder[Throwable])
                                     (handler: (IN) => Future[TransportResponse]): String = {

    val underlyingSubscriptionId = lookupServerTransport(topicFilter).process[IN](
      topicFilter,
      inputDecoder,
      exceptionEncoder)(handler)

    val result = addSubscriptionLink(topicFilter, underlyingSubscriptionId)
    log.info(s"New processor on $topicFilter: #${handler.hashCode.toHexString}. Id = $result")
    result
  }

  def subscribe[IN <: TransportRequest ](topicFilter: Topic, groupName: String,
                                         inputDecoder: Decoder[IN])
                                        (handler: (IN) => Future[Unit]): String = {
    val underlyingSubscriptionId = lookupServerTransport(topicFilter).subscribe[IN](
      topicFilter,
      groupName,
      inputDecoder)(handler)
    val result = addSubscriptionLink(topicFilter, underlyingSubscriptionId)
    log.info(s"New subscription on $topicFilter($groupName): #${handler.hashCode.toHexString}. Id = $result")
    result
  }

  protected def addSubscriptionLink(topic: Topic, underlyingSubscriptionId: String) = {
    val subscriptionId = idCounter.incrementAndGet().toHexString
    subscriptions.put(subscriptionId, (topic, underlyingSubscriptionId))
    subscriptionId
  }

  protected def lookupServerTransport(topic: Topic): ServerTransport = {
    serverRoutes.find(r ⇒ r.urlArg.matchFilter(topic.urlFilter) &&
      r.valueFilters.matchFilters(topic.valueFilters)) map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"TopicFilter: ${topic.urlFilter}/${topic.valueFilters.toString}")
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
