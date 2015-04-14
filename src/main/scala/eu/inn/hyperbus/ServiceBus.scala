package eu.inn.hyperbus

import java.util.concurrent.atomic.AtomicLong

import eu.inn.hyperbus.impl.ServiceBusMacro
import eu.inn.hyperbus.serialization.{Decoder, Encoder}
import eu.inn.hyperbus.transport.{ServerTransport, ClientTransport}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.experimental.macros

class ServiceBus(val defaultClientTransport: ClientTransport, val defaultServerTransport: ServerTransport) {
  protected val clientRoutes = new TrieMap[String, ClientTransport]
  protected val serverRoutes = new TrieMap[String, ServerTransport]
  protected val subscriptionId = new AtomicLong(0)
  protected val subscriptions = new TrieMap[String, String]()

  def send[OUT,IN](
                    topic: String,
                    message: IN
                    ): Future[OUT] = macro ServiceBusMacro.send[OUT,IN]

  def subscribe[OUT,IN](
                         topic: String,
                         groupName: Option[String],
                         handler: (IN) => Future[OUT]
                         ): String = macro ServiceBusMacro.subscribe[OUT,IN]

  def unsubscribe(subscriptionId: String): Unit = {
    val a = subscriptionId.split('-')
    if (a.length == 2) {
      subscriptions.get(a(0)) foreach { topic =>
        lookupServerTransport(topic).unsubscribe(a(1))
      }
    }
  }

  def lookupServerTransport(topic: String): ServerTransport =
    serverRoutes.getOrElse(topic, defaultServerTransport)

  def lookupClientTransport(topic: String): ClientTransport =
    clientRoutes.getOrElse(topic, defaultClientTransport)

  protected[hyperbus] def subscribe[OUT,IN](
      topic: String,
      groupName: Option[String],
      inputDecoder: Decoder[IN],
      outputEncoder: Encoder[OUT],
      handler: (IN) => Future[OUT]
    ): String = {

    val id = this.subscriptionId.incrementAndGet().toHexString + "-" +
      lookupServerTransport(topic: String).subscribe[OUT,IN](
        topic, groupName, inputDecoder, outputEncoder, handler)

    subscriptions.put(id, topic)
    id
  }
}
