package eu.inn.servicebus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.util.ConfigUtils
import eu.inn.servicebus.util.ConfigUtils._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

class DistributedAkkaClientTransport(val actorSystem: ActorSystem,
              val localAffinity: Boolean = true,
              val logMessages: Boolean = false,
              val releaseActorSystem: Boolean = false,
              implicit val executionContext: ExecutionContext = ExecutionContext.global,
              implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

  def this(config: Config) = this(ActorSystemRegistry.addRef(config.getString("actor-system", "eu-inn")),
    config.getOptionBoolean("local-afinity") getOrElse true,
    config.getOptionBoolean("log-messages") getOrElse false,
    true,
    scala.concurrent.ExecutionContext.global, // todo: configurable ExecutionContext like in akka?
    new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected [this] val cluster = Cluster(actorSystem)
  protected [this] val log = LoggerFactory.getLogger(this.getClass)

  /*val noRouteActor = actorSystem.actorSelection("no-route-watcher").resolveOne().recover {
    case _ ⇒ actorSystem.actorOf(Props(new NoRouteWatcher), "no-route-watcher")
  }*/

  protected [this] val mediator = DistributedPubSubExtension(actorSystem).mediator


  override def ask[OUT, IN](topic: Topic,
                            message: IN,
                            inputEncoder: Encoder[IN],
                            outputDecoder: Decoder[OUT]): Future[OUT] = {

    val inputBytes = new ByteArrayOutputStream()
    inputEncoder(message, inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)

    if (logMessages) {
      log.info(s"hyperBus <~ $topic: REQ#${messageString.hashCode} $messageString")
    }

    akka.pattern.ask(mediator, Publish(topic.url, messageString, sendOneMessageToEachGroup = true)) map {
      case result: String ⇒
        if (logMessages) {
          log.info(s"RES#${messageString.hashCode}: $result")
        }
        val outputBytes = new ByteArrayInputStream(result.getBytes(Util.defaultEncoding))
        outputDecoder(outputBytes)
      // todo: case _ ⇒
    }
  }

  override def publish[IN](topic: Topic, message: IN, inputEncoder: Encoder[IN]): Future[Unit] = {
    val inputBytes = new ByteArrayOutputStream()
    inputEncoder(message, inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)

    if (logMessages) {
      log.info(s"hyperBus <| $topic: $messageString")
    }

    mediator ! Publish(topic.url, messageString, sendOneMessageToEachGroup = true) // todo: At least one confirm?
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

