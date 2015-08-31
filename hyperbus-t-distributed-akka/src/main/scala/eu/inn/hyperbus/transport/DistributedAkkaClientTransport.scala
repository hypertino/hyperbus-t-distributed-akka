package eu.inn.hyperbus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.distributedakka.Util
import eu.inn.hyperbus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

class DistributedAkkaClientTransport(val actorSystem: ActorSystem,
              val localAffinity: Boolean = true,
              val logMessages: Boolean = false,
              val releaseActorSystem: Boolean = false,
              implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

  def this(config: Config) = this(ActorSystemRegistry.addRef(config),
    localAffinity = config.getOptionBoolean("local-afinity") getOrElse true,
    logMessages = config.getOptionBoolean("log-messages") getOrElse false,
    true,
    new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected [this] val cluster = Cluster(actorSystem)
  protected [this] val log = LoggerFactory.getLogger(this.getClass)

  /*val noRouteActor = actorSystem.actorSelection("no-route-watcher").resolveOne().recover {
    case _ ⇒ actorSystem.actorOf(Props(new NoRouteWatcher), "no-route-watcher")
  }*/

  protected [this] val mediator = DistributedPubSubExtension(actorSystem).mediator


  override def ask[OUT <: TransportResponse](message: TransportRequest, outputDecoder: Decoder[OUT]): Future[OUT] = {

    val specificUrl = message.topic.urlFilter.specific
    val inputBytes = new ByteArrayOutputStream()
    message.encode(inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)
    import eu.inn.hyperbus.util.LogUtils._

    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("requestId" → messageString.hashCode.toHexString), s"hyperBus <~ $messageString")
    }

    import actorSystem.dispatcher
    akka.pattern.ask(mediator, Publish(specificUrl, messageString, sendOneMessageToEachGroup = true)) map {
      case result: String ⇒
        if (logMessages && log.isTraceEnabled) {
          log.trace(Map("requestId" → messageString.hashCode.toHexString), s"hyperBus ~(R)~> $result")
        }
        val outputBytes = new ByteArrayInputStream(result.getBytes(Util.defaultEncoding))
        outputDecoder(outputBytes)
      // todo: case _ ⇒
    }
  }

  override def publish(message: TransportRequest): Future[Unit] = {
    val specificUrl = message.topic.urlFilter.specific
    val inputBytes = new ByteArrayOutputStream()
    message.encode(inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)

    if (logMessages && log.isTraceEnabled) {
      log.trace(s"hyperBus <| $messageString")
    }

    mediator ! Publish(specificUrl, messageString, sendOneMessageToEachGroup = true) // todo: At least one confirm?
    Future.successful{}
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    log.info("Shutting down DistributedAkkaClientTransport...")
    if (releaseActorSystem) {
      log.debug(s"DistributedAkkaClientTransport: releasing ActorSystem(${actorSystem.name})")
      ActorSystemRegistry.release(actorSystem.name)(duration)
    }
    Future.successful(true)
  }
}

