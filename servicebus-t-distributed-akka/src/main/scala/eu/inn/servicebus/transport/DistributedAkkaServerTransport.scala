package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonManager
import akka.pattern.gracefulStop
import com.typesafe.config.Config
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.distributedakka.{ProcessServerActor, Start, SubscribeServerActor}
import eu.inn.servicebus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class DistributedAkkaServerTransport(val actorSystem: ActorSystem,
                                     val logMessages: Boolean = false,
                                     val releaseActorSystem: Boolean = false,
                                     implicit val executionContext: ExecutionContext = ExecutionContext.global)
  extends ServerTransport {

  def this(config: Config) = this(ActorSystemRegistry.addRef(config),
    config.getOptionBoolean("log-messages") getOrElse false,
    true,
    scala.concurrent.ExecutionContext.global)

  protected [this] val subscriptions = new TrieMap[String, ActorRef]
  protected [this] val cluster = Cluster(actorSystem)
  protected [this] val idCounter = new AtomicLong(0)
  protected [this] val log = LoggerFactory.getLogger(this.getClass)

  override def process[OUT, IN](topic: Topic,
                           inputDecoder: Decoder[IN],
                           partitionArgsExtractor: PartitionArgsExtractor[IN],
                           exceptionEncoder: Encoder[Throwable])
                          (handler: (IN) ⇒ SubscriptionHandlerResult[OUT]): String = {

    val id = idCounter.incrementAndGet().toHexString
    val actor = actorSystem.actorOf(Props[ProcessServerActor[OUT,IN]], "eu-inn-distr-process-server" + id) // todo: unique id?
    subscriptions.put(id, actor)
    actor ! Start(id,
      distributedakka.Subscription[OUT, IN](topic, None, inputDecoder, partitionArgsExtractor, exceptionEncoder, handler),
      logMessages
    )
    id
  }

  override def subscribe[IN](topic: Topic,
                             groupName: String,
                             inputDecoder: Decoder[IN],
                             partitionArgsExtractor: PartitionArgsExtractor[IN])
                            (handler: (IN) ⇒ SubscriptionHandlerResult[Unit]): String = {
    val id = idCounter.incrementAndGet().toHexString
    val actor = actorSystem.actorOf(Props[SubscribeServerActor[IN]], "eu-inn-distr-subscribe-server" + id) // todo: unique id?
    subscriptions.put(id, actor)
    actor ! Start(id,
      distributedakka.Subscription[Unit, IN](topic, Some(groupName), inputDecoder, partitionArgsExtractor, null, handler),
      logMessages
    )
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
      cluster.down(cluster.selfAddress)
      cluster.leave(cluster.selfAddress)
      Thread.sleep(500) // todo: replace this with event, wait while cluster.leave completes
      if (releaseActorSystem) {
        log.debug(s"DistributedAkkaServerTransport: releasing ActorSystem(${actorSystem.name})")
        ActorSystemRegistry.release(actorSystem.name)(duration)
      }
      true
    }
  }
}

