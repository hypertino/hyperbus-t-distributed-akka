package eu.inn.servicebus.transport.distributedakka

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.ActorSystem
import akka.contrib.pattern.DistributedPubSubExtension
import akka.contrib.pattern.DistributedPubSubMediator.Publish
import akka.util.Timeout
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.transport.{ClientTransport, Topic}

import scala.concurrent.{ExecutionContext, Future}

class DistributedAkkaClientTransport(val actorSystem: ActorSystem = Util.akkaSystem,
              val localAffinity: Boolean = true,
              implicit val executionContext: ExecutionContext = ExecutionContext.global,
              implicit val timeout: Timeout = Util.defaultTimeout) extends ClientTransport {

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

