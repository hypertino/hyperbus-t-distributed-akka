package eu.inn.hyperbus.transport.api

import java.util.concurrent.atomic.AtomicLong

import eu.inn.hyperbus.transport.api.matchers.TransportRequestMatcher
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
class TransportManager(protected[this] val clientRoutes: Seq[TransportRoute[ClientTransport]],
                       protected[this] val serverRoutes: Seq[TransportRoute[ServerTransport]],
                       implicit protected[this] val executionContext: ExecutionContext) extends TransportManagerApi {

  protected[this] val subscriptions = new TrieMap[String, (TransportRequestMatcher, String)]
  protected[this] val idCounter = new AtomicLong(0)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  def this(configuration: TransportConfiguration) = this(configuration.clientRoutes,
    configuration.serverRoutes, ExecutionContext.global)

  def ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT]): Future[OUT] = {
    this.lookupClientTransport(message).ask[OUT](message, outputDeserializer)
  }

  def publish(message: TransportRequest): Future[PublishResult] = {
    this.lookupClientTransport(message).publish(message)
  }

  protected def lookupClientTransport(message: TransportRequest): ClientTransport = {
    clientRoutes.find { route ⇒
      route.matcher.matchMessage(message)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"${message.uri} with headers: ${message.headers}")
    }
  }

  def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach(s ⇒ lookupServerTransport(s._1).off(s._2))
    subscriptions.remove(subscriptionId)
  }

  def onCommand[IN <: TransportRequest](requestMatcher: TransportRequestMatcher,
                                        inputDeserializer: Deserializer[IN])
                                       (handler: (IN) => Future[TransportResponse]): String = {

    val underlyingSubscriptionId = lookupServerTransport(requestMatcher).onCommand[IN](
      requestMatcher,
      inputDeserializer)(handler)

    val result = addSubscriptionLink(requestMatcher, underlyingSubscriptionId)
    log.info(s"New processor on $requestMatcher: #${handler.hashCode.toHexString}. Id = $result")
    result
  }

  def onEvent[IN <: TransportRequest](requestMatcher: TransportRequestMatcher, groupName: String,
                                      inputDeserializer: Deserializer[IN])
                                     (handler: (IN) => Future[Unit]): String = {
    val underlyingSubscriptionId = lookupServerTransport(requestMatcher).onEvent[IN](
      requestMatcher,
      groupName,
      inputDeserializer)(handler)
    val result = addSubscriptionLink(requestMatcher, underlyingSubscriptionId)
    log.info(s"New subscription on $requestMatcher($groupName): #${handler.hashCode.toHexString}. Id = $result")
    result
  }

  protected def addSubscriptionLink(requestMatcher: TransportRequestMatcher, underlyingSubscriptionId: String) = {
    val subscriptionId = idCounter.incrementAndGet().toHexString
    subscriptions.put(subscriptionId, (requestMatcher, underlyingSubscriptionId))
    subscriptionId
  }

  protected def lookupServerTransport(requestMatcher: TransportRequestMatcher): ServerTransport = {
    serverRoutes.find { route ⇒
      route.matcher.matchRequestMatcher(requestMatcher)
    } map (_.transport) getOrElse {
      throw new NoTransportRouteException(s"${requestMatcher.uri} with header matchers: ${requestMatcher.headers}")
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    val client = Future.sequence(clientRoutes.map(_.transport.shutdown(duration)))
    val server = Future.sequence(serverRoutes.map(_.transport.shutdown(duration)))
    client flatMap { c ⇒
      server map { s ⇒
        s.forall(_ == true) && c.forall(_ == true)
      }
    }
  }
}

