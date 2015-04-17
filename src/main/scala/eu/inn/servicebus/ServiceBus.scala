package eu.inn.servicebus

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.impl.{Subscriptions, ServiceBusMacro}
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.transport.{ClientTransport, ServerTransport}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.experimental.macros

class ServiceBus(val defaultClientTransport: ClientTransport, val defaultServerTransport: ServerTransport) {
  protected val clientRoutes = new TrieMap[String, ClientTransport]
  protected val serverRoutes = new TrieMap[String, ServerTransport]
  protected val subscriptions = new Subscriptions[String]

  def send[OUT,IN](
                    topic: String,
                    message: IN
                    ): Future[OUT] = macro ServiceBusMacro.send[OUT,IN]

  def send[OUT,IN](
                    topic: String,
                    message: IN,
                    outputDecoder: Decoder[OUT],
                    inputEncoder: Encoder[IN]
                    ): Future[OUT] = {
    this.lookupClientTransport(topic).send[OUT,IN](topic,message,outputDecoder,inputEncoder)
  }

  def subscribe[OUT,IN](
                         topic: String,
                         groupName: Option[String],
                         handler: (IN) => Future[OUT]
                         ): String = macro ServiceBusMacro.subscribe[OUT,IN]

  def unsubscribe(subscriptionId: String): Unit = {
    subscriptions.getRouteByKeyId(subscriptionId) foreach { topic =>
      subscriptions.get(topic) foreach { case (_,subscrSeq) =>
        subscrSeq.find(_.subscriptionId == subscriptionId).foreach {
          underlyingSubscription =>
            lookupServerTransport(topic).unsubscribe(underlyingSubscription.subcription)
        }
      }
    }
    subscriptions.remove(subscriptionId)
  }
  def subscribe[OUT,IN](
                         topic: String,
                         groupName: Option[String],
                         inputDecoder: Decoder[IN],
                         outputEncoder: Encoder[OUT],
                         handler: (IN) => Future[OUT]
                         ): String = {

    val underlyingSubscriptionId = lookupServerTransport(topic: String).subscribe[OUT,IN](
        topic, groupName, inputDecoder, outputEncoder, handler)

    subscriptions.add(topic,None,underlyingSubscriptionId)
  }

  protected def lookupServerTransport(topic: String): ServerTransport =
    serverRoutes.getOrElse(topic, defaultServerTransport)

  protected def lookupClientTransport(topic: String): ClientTransport =
    clientRoutes.getOrElse(topic, defaultClientTransport)
}
