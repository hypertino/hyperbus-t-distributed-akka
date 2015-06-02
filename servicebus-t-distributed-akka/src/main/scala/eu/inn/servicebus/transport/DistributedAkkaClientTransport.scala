package eu.inn.servicebus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.ActorSystem
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.typesafe.config.Config
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.util.ConfigUtils

import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.{ExecutionContext, Future}
import ConfigUtils._

class DistributedAkkaClientTransport(val actorSystem: ActorSystem = Util.akkaSystem,
              val localAffinity: Boolean = true,
              implicit val executionContext: ExecutionContext = ExecutionContext.global,
              implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

  def this(config: Config) = this(
    config.getOptionString("actor-system").map { ActorSystem(_) } getOrElse Util.akkaSystem,
    config.getOptionBoolean("local-afinity") getOrElse true,
    scala.concurrent.ExecutionContext.global, // todo: configurable ExecutionContext like in akka?
    new Timeout(config.getOptionDuration("timeout") getOrElse Util.defaultTimeout)
  )

  val mediator = DistributedPubSubExtension(actorSystem).mediator

  override def ask[OUT, IN](topic: Topic,
                            message: IN,
                            inputEncoder: Encoder[IN],
                            outputDecoder: Decoder[OUT]): Future[OUT] = {

    val inputBytes = new ByteArrayOutputStream()
    inputEncoder(message, inputBytes)
    val messageString = inputBytes.toString(Util.defaultEncoding)

    akka.pattern.ask(mediator, Publish(topic.url, messageString, sendOneMessageToEachGroup = true)) map {
      case result: String ⇒ {
        val outputBytes = new ByteArrayInputStream(result.getBytes(Util.defaultEncoding))
        outputDecoder(outputBytes)
      }
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
}

