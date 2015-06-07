package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import akka.actor._
import com.typesafe.config.Config
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.distributedakka.{OnServerActor, Start, SubscribeServerActor}
import eu.inn.servicebus.util.ConfigUtils._

import scala.collection.concurrent.TrieMap

class DistributedAkkaServerTransport(val actorSystem: ActorSystem) extends ServerTransport {

  def this(config: Config) = this(ActorSystemRegistry.getOrCreate(config.getString("actor-system", "eu-inn")))

  val subscriptions = new TrieMap[String, ActorRef]
  protected val idCounter = new AtomicLong(0)

  override def on[OUT, IN](topic: Topic,
                           inputDecoder: Decoder[IN],
                           partitionArgsExtractor: PartitionArgsExtractor[IN],
                           exceptionEncoder: Encoder[Throwable])
                          (handler: (IN) ⇒ SubscriptionHandlerResult[OUT]): String = {

    val actor = actorSystem.actorOf(Props[OnServerActor[OUT,IN]])
    val id = idCounter.incrementAndGet().toHexString
    subscriptions.put(id, actor)
    actor ! Start(id, distributedakka.Subscription[OUT, IN](topic, None, inputDecoder, partitionArgsExtractor, exceptionEncoder, handler))
    id
  }

  override def subscribe[IN](topic: Topic,
                             groupName: String,
                             inputDecoder: Decoder[IN],
                             partitionArgsExtractor: PartitionArgsExtractor[IN])
                            (handler: (IN) ⇒ SubscriptionHandlerResult[Unit]): String = {
    val actor = actorSystem.actorOf(Props[SubscribeServerActor[IN]])
    val id = idCounter.incrementAndGet().toHexString
    subscriptions.put(id, actor)
    actor ! Start(id, distributedakka.Subscription[Unit, IN](topic, Some(groupName), inputDecoder, partitionArgsExtractor, null, handler))
    id
  }

  override def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach{ s⇒
      actorSystem.stop(s)
      subscriptions.remove(subscriptionId)
    }
  }
}

