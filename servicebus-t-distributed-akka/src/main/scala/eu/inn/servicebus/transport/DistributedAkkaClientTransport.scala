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

class DistributedAkkaClientTransport(val actorSystemName: String,
              val localAffinity: Boolean = true,
              implicit val executionContext: ExecutionContext = ExecutionContext.global,
              implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

  def this(config: Config) = this(config.getString("actor-system", "eu-inn"),
    config.getOptionBoolean("local-afinity") getOrElse true,
    scala.concurrent.ExecutionContext.global, // todo: configurable ExecutionContext like in akka?
    new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  protected [this] val actorSystem = ActorSystemRegistry.addRef(actorSystemName)
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

    akka.pattern.ask(mediator, Publish(topic.url, messageString, sendOneMessageToEachGroup = true)) map {
      case result: String ⇒
        val outputBytes = new ByteArrayInputStream(result.getBytes(Util.defaultEncoding))
        outputDecoder(outputBytes)
      // todo: case _ ⇒
    }
  }

  override def publish[IN](topic: Topic, message: IN, inputEncoder: Encoder[IN]): Future[Unit] = {
    val inputBytes = new ByteArrayOutputStream()
    inputEncoder(message, inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)
    mediator ! Publish(topic.url, messageString, sendOneMessageToEachGroup = true) // todo: At least one confirm?
    Future.successful{}
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    log.info("Shutting down DistributedAkkaClientTransport...")
    log.debug(s"DistributedAkkaClientTransport: releasing ActorSystem($actorSystemName)")
    ActorSystemRegistry.release(actorSystemName)(duration)
    Future.successful(true)
  }
}

