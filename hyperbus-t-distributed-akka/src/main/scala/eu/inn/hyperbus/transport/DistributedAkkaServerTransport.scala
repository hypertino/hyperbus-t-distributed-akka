package eu.inn.hyperbus.transport

import java.util.concurrent.atomic.AtomicLong

import akka.actor._
import akka.cluster.Cluster
import akka.pattern.gracefulStop
import com.typesafe.config.Config
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.distributedakka.{ProcessServerActor, Start, SubscribeServerActor}
import eu.inn.hyperbus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DistributedAkkaServerTransport(val actorSystem: ActorSystem,
                                     val logMessages: Boolean = false,
                                     val releaseActorSystem: Boolean = false)
  extends ServerTransport {

  def this(config: Config) = this(ActorSystemRegistry.addRef(config),
    logMessages = config.getOptionBoolean("log-messages") getOrElse false,
    true)

  protected[this] val subscriptions = new TrieMap[String, ActorRef]
  protected[this] val cluster = Cluster(actorSystem)
  protected[this] val idCounter = new AtomicLong(0)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  override def process[IN <: TransportRequest](topicFilter: Topic, inputDeserializer: Deserializer[IN], exceptionSerializer: Serializer[Throwable])
                                              (handler: (IN) => Future[TransportResponse]): String = {

    val topicUrl = topicFilter.urlFilter.asInstanceOf[SpecificValue].value // currently only Specific url's are supported, todo: add Regex, Any, etc...
    val id = idCounter.incrementAndGet().toHexString
    val actor = actorSystem.actorOf(Props[ProcessServerActor[IN]], "eu-inn-distr-process-server" + id) // todo: unique id?
    subscriptions.put(id, actor)
    actor ! Start(id,
      distributedakka.Subscription[TransportResponse, IN](topicUrl, topicFilter, None, inputDeserializer, exceptionSerializer, handler),
      logMessages
    )
    id
  }

  override def subscribe[IN <: TransportRequest](topicFilter: Topic, groupName: String, inputDeserializer: Deserializer[IN])
                                                (handler: (IN) => Future[Unit]): String = {
    val topicUrl = topicFilter.urlFilter.asInstanceOf[SpecificValue].value // currently only Specific url's are supported, todo: add Regex, Any, etc...
    val id = idCounter.incrementAndGet().toHexString
    val actor = actorSystem.actorOf(Props[SubscribeServerActor[IN]], "eu-inn-distr-subscribe-server" + id) // todo: unique id?
    subscriptions.put(id, actor)
    actor ! Start(id,
      distributedakka.Subscription[Unit, IN](topicUrl, topicFilter, Some(groupName), inputDeserializer, null, handler),
      logMessages
    )
    id
  }

  override def off(subscriptionId: String): Unit = {
    subscriptions.get(subscriptionId).foreach { s ⇒
      actorSystem.stop(s)
      subscriptions.remove(subscriptionId)
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    log.info("Shutting down DistributedAkkaServerTransport...")
    import actorSystem.dispatcher
    val actorStopFutures = subscriptions.map(s ⇒
      gracefulStop(s._2, duration) recover {
        case t: Throwable ⇒
          log.error("Shutting down distributed akka", t)
          false
      }
    )

    Future.sequence(actorStopFutures) map { list ⇒
      val result = list.forall(_ == true)
      subscriptions.clear()
      //cluster.down(cluster.selfAddress)
      Thread.sleep(500) // todo: replace this with event, wait while cluster.leave completes
      if (releaseActorSystem) {
        log.debug(s"DistributedAkkaServerTransport: releasing ActorSystem(${actorSystem.name})")
        ActorSystemRegistry.release(actorSystem.name)(duration)
      }
      true
    }
  }
}

