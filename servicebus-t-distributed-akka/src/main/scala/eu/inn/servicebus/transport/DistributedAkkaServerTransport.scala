package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonManager
import com.typesafe.config.Config
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.distributedakka.{AutoDownControlActor, OnServerActor, Start, SubscribeServerActor}
import eu.inn.servicebus.util.ConfigUtils._

import scala.collection.concurrent.TrieMap

class DistributedAkkaServerTransport(val actorSystem: ActorSystem) extends ServerTransport {

  def this(config: Config) = this(ActorSystemRegistry.getOrCreate(config.getString("actor-system", "eu-inn")))

  val subscriptions = new TrieMap[String, ActorRef]
  protected val idCounter = new AtomicLong(0)

  if (Cluster(actorSystem).getSelfRoles.contains("auto-down-controller")) {
    actorSystem.actorOf(ClusterSingletonManager.props(
      Props(classOf[AutoDownControlActor]),
      "control-auto-down-singleton",
      PoisonPill,
      Some("auto-down-controller"))
    )
  }

  override def on[OUT, IN](topic: Topic,
                           inputDecoder: Decoder[IN],
                           partitionArgsExtractor: PartitionArgsExtractor[IN],
                           exceptionEncoder: Encoder[Throwable])
                          (handler: (IN) ⇒ SubscriptionHandlerResult[OUT]): String = {

    val id = idCounter.incrementAndGet().toHexString
    val actor = actorSystem.actorOf(Props[OnServerActor[OUT,IN]], "eu-inn-distr-on-server" + id)
    subscriptions.put(id, actor)
    actor ! Start(id, distributedakka.Subscription[OUT, IN](topic, None, inputDecoder, partitionArgsExtractor, exceptionEncoder, handler))
    id
  }

  override def subscribe[IN](topic: Topic,
                             groupName: String,
                             inputDecoder: Decoder[IN],
                             partitionArgsExtractor: PartitionArgsExtractor[IN])
                            (handler: (IN) ⇒ SubscriptionHandlerResult[Unit]): String = {
    val id = idCounter.incrementAndGet().toHexString
    val actor = actorSystem.actorOf(Props[SubscribeServerActor[IN]], "eu-inn-distr-subscribe-server" + id)
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

