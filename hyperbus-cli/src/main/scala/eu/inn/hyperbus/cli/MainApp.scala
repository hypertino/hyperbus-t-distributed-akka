package eu.inn.hyperbus.cli

import akka.actor.Address
import akka.cluster.Cluster
import com.fasterxml.jackson.core.JsonFactory
import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.Text
import eu.inn.binders.naming.PlainConverter
import eu.inn.hyperbus.{HyperBus, IdGenerator}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.ActorSystemRegistry
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.Breaks._

trait Commands
case class InitCommand(hyperBus: HyperBus) extends Commands
case class InputCommand(message: String) extends Commands

@body("application/vnd+test-body.json")
case class TestBody(content: Option[String]) extends Body

@request("/test")
case class TestRequest(body: TestBody) extends StaticGet(body)
with DefinedResponse[Ok[TestBody]]

object MainApp {
  val console = new jline.console.ConsoleReader
  //console.getTerminal.setEchoEnabled(false)
  //console.setPrompt(">")

  def main(args : Array[String]): Unit = {

    println("Starting hyperbus-cli...")
    val config = ConfigFactory.load()
    val transportConfiguration = TransportConfigurationLoader.fromConfig(config)
    val transportManager = new TransportManager(transportConfiguration)
    val hyperBus = new HyperBus(transportManager)

    val actorSystem = ActorSystemRegistry.get("eu-inn").get

    hyperBus ~> { r: TestRequest ⇒
      Future.successful {
        Ok(DynamicBody(Text(s"Received content: ${r.body.content}")))
      }
    }

    def quit(): Unit = {
      out("Exiting...")
      val timeout = 30.seconds
      Await.result(hyperBus.shutdown(timeout), timeout)
    }

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run() {
        quit()
      }
    })

    val askCommand = """^~>\s*(.+)\s+(.+)\s+(.+)\s+(.+)$""".r
    val publishCommand = """^\|>\s*(.+)\s+(.+)\s+(.+)\s+(.+)$""".r
    val downMember = """^down (.+)://(.+)@(.+):(\d+)$""".r
    val leaveMember = """^leave (.+)://(.+)@(.+):(\d+)$""".r

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

        case downMember(protocol,system,host,port) ⇒
          val cluster = Cluster(actorSystem)
          val address = Address(protocol,system,host,port.toInt)
          out("Downing: " + address)
          cluster.down(address)

        case leaveMember(protocol,system,host,port) ⇒
          val cluster = Cluster(actorSystem)
          val address = Address(protocol,system,host,port.toInt)
          out("Leaving: " + address)
          cluster.leave(address)

        case cmd ⇒
          out(s"""|Invalid command received: '$cmd', available: ~>, |>, quit
            |Example: ~> get /test application/vnd+test-body.json {"someValue":"abc"}""".stripMargin('|'))
      }
    }}

    quit()
  }

  def createDynamicRequest(method: String, url: String, contentType: Option[String], body: String): DynamicRequest = {
    val jf = new JsonFactory()
    val jp = jf.createParser(body)
    try {
      DynamicRequest(RequestHeader(url, method, contentType, IdGenerator.create(), None), jp)
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
