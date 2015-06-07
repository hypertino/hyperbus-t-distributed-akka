package eu.inn.hyperbus.cli

import com.fasterxml.jackson.core.JsonFactory
import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.Text
import eu.inn.binders.naming.PlainConverter
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.annotations.{contentType, url}
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.servicebus.transport.ActorSystemRegistry
import eu.inn.servicebus.{ServiceBus, ServiceBusConfigurationLoader}

import jline.UnixTerminal

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.Breaks._

trait Commands
case class InitCommand(hyperBus: HyperBus) extends Commands
case class InputCommand(message: String) extends Commands

@contentType("application/vnd+test-body.json")
case class TestBody(content: Option[String]) extends Body

@url("/test")
case class TestRequest(body: TestBody) extends StaticGet(body)
with DefinedResponse[Ok[TestBody]]

object MainApp {
  val console = new jline.console.ConsoleReader
  //console.getTerminal.setEchoEnabled(false)
  //console.setPrompt(">")

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

    val askCommand = """^~>\s*(.+)\s+(.+)\s+(.+)\s+(.+)$""".r
    val publishCommand = """^\|>\s*(.+)\s+(.+)\s+(.+)\s+(.+)$""".r
    breakable{ while(true) {
      console.readLine(">") match {
        case "quit" ⇒ break()

        case askCommand(method, url, contentType, body) ⇒
          val r = createDynamicRequest(method, url, Some(contentType), body)
          out(s"<~$r")
          val f = hyperBus <~ r
          printResponse(f)

        case publishCommand(method, url, contentType, body) ⇒
          val r = createDynamicRequest(method, url, Some(contentType), body)
          out(s"<!$r")
          val f = hyperBus <| r

        case cmd ⇒
          out(s"""|Invalid command received: '$cmd', available: ~>, |>, quit
            |Example: ~> get /test application/vnd+test-body.json {"someValue":"abc"}""".stripMargin('|'))
      }
    }}

    ActorSystemRegistry.get("eu-inn").foreach(_.shutdown()) // todo: normal shutdown without hack
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
      case x ⇒ out(x)
    }
  }

  def out(s: Any): Unit = {
    s match {
      case r: Request[Body] ⇒ outx(r)
      case r: Response[Body] ⇒ outx(r)
      case _ ⇒
        console.println(s.toString)
        console.accept()
    }
  }

  def outx(r: Request[Body]): Unit = {
    console.println(s"-> ${r.getClass.getName}:{ ${r.method} ${r.url} @ ${r.body.contentType}\n----------")
    outx(r.body)
    console.println("----------")
    console.accept()
  }

  def outx(r: Response[Body]): Unit = {
    console.println(s"<- ${r.getClass.getName}:{ ${r.status} @ ${r.body.contentType}\n----------")
    outx(r.body)
    console.println("----------")
    console.accept()
  }

  def outx(r: Body): Unit = {
    import eu.inn.binders.json._
    implicit val defaultSerializerFactory = new DefaultSerializerFactory[PlainConverter](true)
    r match {
      case d: DynamicBody ⇒
        console.println(d.content.toJson)
      case t ⇒
        console.println(t.toString)
    }
  }
}
