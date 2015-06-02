package eu.inn.hyperbus.cli

import akka.actor.{Props, ActorSystem, Actor}
import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.{Text, Value}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest.{Body, DynamicBody, DefinedResponse}
import eu.inn.hyperbus.rest.annotations.{contentType, url}
import eu.inn.hyperbus.rest.standard._
import eu.inn.servicebus.{ServiceBusConfigurationLoader, ServiceBus}

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.control.Breaks._
import scala.concurrent.ExecutionContext.Implicits.global

trait Commands
case class InitCommand(hyperBus: HyperBus) extends Commands
case class InputCommand(message: String) extends Commands

class CommandActor extends Actor {
  var hyperBus: HyperBus = null

  override def receive: Receive = {
    case InitCommand(h) ⇒ this.hyperBus = h

    case InputCommand("ask") ⇒
      print("Please provide request message\n>")
      context.become(ask)

    case InputCommand(msg) ⇒
      print(s"Invalid command received: '$msg', available: ask, publish, quit\n>")
  }

  def ask: Receive = {
    case InputCommand(s) ⇒ {

      import eu.inn.binders.json._
      val request = DynamicGet("/test", DynamicBody(s.parseJson[Value]))

      /*hyperBus ? request map {
        response ⇒
          println(response)
      }*/

      //import eu.inn.hyperbus.{serialization ⇒ hbs}
      //val encoder = hbs.createEncoder[DynamicGet[DynamicBody]]


      /*val responseDecoder = hyperBus.responseDecoder(
        _: hbs.ResponseHeader,
        _: com.fasterxml.jackson.core.JsonParser,
        null
      )
      val decoder = eu.inn.hyperbus.serialization.createRequestDecoder[Dynamic]
      hyperBus.ask(get, encoder,null,responseDecoder) map { result ⇒
        print(s"Response: $result\n>")
      }*/
    }
  }
}

@contentType("test-body")
case class TestBody(content: DynamicBody) extends Body

@url("/test")
case class TestRequest(body: TestBody) extends StaticPost(body)
with DefinedResponse[Ok[TestBody]]

object MainApp {
  def main(args : Array[String]): Unit = {

    println("Starting hyperbus-cli...")
    val config = ConfigFactory.load()
    val serviceBusConfig = ServiceBusConfigurationLoader.fromConfig(config)
    val serviceBus = new ServiceBus(serviceBusConfig)
    val hyperBus = new HyperBus(serviceBus)

    hyperBus.on[TestRequest] { r⇒
      Future.successful {
        Ok(DynamicBody(Text(s"Received: ${r.body.toString}")))
      }
    }

    val actorSystem = ActorSystem()
    val commandActor = actorSystem.actorOf(Props[CommandActor])

    print(">")
    breakable{ for (cmd ← stdInIterator()) {
      cmd match {
        case "quit" ⇒ break()
        case _ ⇒ commandActor ! cmd
      }
    }}

    println("Exiting...")
    actorSystem.stop(commandActor)
    Thread.sleep(100)
    actorSystem.shutdown()
    actorSystem.awaitTermination()
  }

  def stdInIterator(): Iterator[String] = new Iterator[String] {
    override def hasNext: Boolean = true
    override def next(): String = StdIn.readLine()
  }
}
