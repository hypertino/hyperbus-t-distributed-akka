package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonManager
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.distributedakka.{AutoDownControlActor, OnServerActor, Start, SubscribeServerActor}
import eu.inn.servicebus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.concurrent.duration.{FiniteDuration, Duration}
import akka.pattern.gracefulStop

class DistributedAkkaServerTransport(val actorSystemName: String,
                                     implicit val executionContext: ExecutionContext = ExecutionContext.global)
  extends ServerTransport {

  def this(config: Config) = this(config.getString("actor-system", "eu-inn"),
    scala.concurrent.ExecutionContext.global)

  protected [this] val subscriptions = new TrieMap[String, ActorRef]
  protected [this] val actorSystem = ActorSystemRegistry.addRef(actorSystemName)
  protected [this] val cluster = Cluster(actorSystem)
  protected [this] val idCounter = new AtomicLong(0)
  protected [this] val log = LoggerFactory.getLogger(this.getClass)

  if (cluster.getSelfRoles.contains("auto-down-controller")) {
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

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    log.info("Shutting down DistributedAkkaServerTransport...")
    val actorStopFutures = subscriptions.map(s ⇒
      gracefulStop(s._2, duration) recover {
        case t: Throwable ⇒
          log.error("Shutting down ditributed akka", t)
          false
      }
    )

    Future.sequence(actorStopFutures) map { list ⇒
      val result = list.forall(_ == true)
      subscriptions.clear()
      // cluster.leave(cluster.selfAddress) // todo: implement this
      log.debug(s"DistributedAkkaServerTransport: releasing ActorSystem($actorSystemName)")
      ActorSystemRegistry.release(actorSystemName)(duration)
      true
    }
  }
}

