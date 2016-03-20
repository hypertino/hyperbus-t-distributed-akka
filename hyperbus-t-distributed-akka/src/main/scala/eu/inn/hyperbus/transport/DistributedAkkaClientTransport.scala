package eu.inn.hyperbus.transport

import java.io.ByteArrayInputStream

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.hyperbus.serialization.StringSerializer
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.distributedakka._
import eu.inn.hyperbus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DistributedAkkaClientTransport(val actorSystem: ActorSystem,
                                     val localAffinity: Boolean = true,
                                     val actorSystemRegistryKey: Option[String] = None,
                                     implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

  private def this(actorSystemWrapper: ActorSystemWrapper, localAffinity: Boolean, timeout: Timeout) =
    this(actorSystemWrapper.actorSystem, localAffinity, Some(actorSystemWrapper.key), timeout)

  def this(config: Config) = this(ActorSystemRegistry.addRef(config),
    localAffinity = config.getOptionBoolean("local-afinity") getOrElse true,
    new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected[this] val cluster = Cluster(actorSystem)
  protected[this] val log = LoggerFactory.getLogger(this.getClass)

  import actorSystem._

  val noRouteActor = actorSystem.actorSelection("no-route-watcher").resolveOne().recover {
    case _ ⇒ actorSystem.actorOf(Props(new NoRouteWatcher), "no-route-watcher")
  }

  protected[this] val mediator = DistributedPubSubEx(actorSystem).mediator


  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = {
    val specificUri = message.uri.pattern.specific
    val content = StringSerializer.serializeToString(message)
    val request = HyperbusRequest(content)

    import actorSystem.dispatcher
    akka.pattern.ask(mediator, Publish(specificUri, request, sendOneMessageToEachGroup = true)) map {
      case result: HyperbusResponse ⇒
        val outputBytes = new ByteArrayInputStream(result.content.getBytes(StringSerializer.defaultEncoding))
        outputDeserializer(outputBytes)
    }
  }

  override def publish(message: TransportRequest): Future[PublishResult] = {
    val specificUri = message.uri.pattern.specific
    val content = StringSerializer.serializeToString(message)
    val request = HyperbusRequest(content)

    mediator ! Publish(specificUri, request, sendOneMessageToEachGroup = true) // todo: At least one confirm?
    Future.successful {
      new PublishResult {
        def sent = None

        def offset = None

        override def toString = s"PublishResult(sent=None,offset=None)"
      }
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    log.info("Shutting down DistributedAkkaClientTransport...")
    actorSystemRegistryKey foreach { key ⇒
      log.debug(s"DistributedAkkaClientTransport: releasing ActorSystem(${actorSystem.name}) key: $key")
      ActorSystemRegistry.release(key)(duration)
    }
    Future.successful(true)
  }
}

