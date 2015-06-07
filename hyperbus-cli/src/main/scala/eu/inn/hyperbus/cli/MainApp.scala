package eu.inn.hyperbus.cli

import akka.actor.{Props, ActorSystem, Actor}
import com.fasterxml.jackson.core.JsonFactory
import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.{Text, Value}
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.annotations.{contentType, url}
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.servicebus.{ServiceBusConfigurationLoader, ServiceBus}

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.control.Breaks._
import scala.concurrent.ExecutionContext.Implicits.global

trait Commands
case class InitCommand(hyperBus: HyperBus) extends Commands
case class InputCommand(message: String) extends Commands

@contentType("application/vnd+test-body.json")
case class TestBody(content: Option[String]) extends Body

@url("/test")
case class TestRequest(body: TestBody) extends StaticGet(body)
with DefinedResponse[Ok[TestBody]]

object MainApp {
  def main(args : Array[String]): Unit = {

    println("Starting hyperbus-cli...")
    val config = ConfigFactory.load()
    val serviceBusConfig = ServiceBusConfigurationLoader.fromConfig(config)
    val serviceBus = new ServiceBus(serviceBusConfig)
    val hyperBus = new HyperBus(serviceBus)

    hyperBus ~> { r: TestRequest ⇒
      Future.successful {
        Ok(DynamicBody(Text(s"Received content: ${r.body.content}")))
      }
    }

    out("")
    val askCommand = "^~> (.+) (.+) (.+) (.+)$".r
    val publishCommand = "^|> (.+) (.+) (.+) (.+)$".r
    breakable{ for (cmd ← stdInIterator()) {
      cmd match {
        case "quit" ⇒ break()

        case askCommand(method, url, contentType, body) ⇒
          val r = createDynamicRequest(method, url, Some(contentType), body)
          out(s"<~$r")
          val f = hyperBus <~ r
          printResponse(f)

        case publishCommand(method, url, contentType, body) ⇒
          val r = createDynamicRequest(method, url, Some(contentType), body)
          out(s"<|$r")
          val f = hyperBus <| r

        case _ ⇒
          out(s"""|Invalid command received: '$cmd', available: ~>, |>, quit
            |Example: ~> get /test application/vnd+test-body.json {"someValue":"abc"}""".stripMargin('|'))
      }
    }}

    println("Exiting...")
  }

  def createDynamicRequest(method: String, url: String, contentType: Option[String], body: String): DynamicRequest[DynamicBody] = {
    val jf = new JsonFactory()
    val jp = jf.createParser(body)
    try {
      eu.inn.hyperbus.impl.Helpers.decodeDynamicRequest(RequestHeader(url, method, contentType), jp)
    } finally {
      jp.close()
    }
  }

  def printResponse(response: Future[Response[Body]]) = {
    response map out recover{
      case x: Throwable ⇒ out(x.toString)
    }
  }

  def stdInIterator(): Iterator[String] = new Iterator[String] {
    override def hasNext: Boolean = true
    override def next(): String = StdIn.readLine().trim
  }

  def out(s: Any): Unit = {
    println(s)
    print(">")
  }
}
