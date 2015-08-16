package eu.inn.hyperbus.cli

import akka.actor.Address
import akka.cluster.Cluster
import com.fasterxml.jackson.core.JsonFactory
import com.typesafe.config.ConfigFactory
import eu.inn.binders.dynamic.Text
import eu.inn.binders.naming.PlainConverter
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.annotations.{contentType, url}
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.utils.IdUtils
import eu.inn.servicebus.transport.ActorSystemRegistry
import eu.inn.servicebus.{ServiceBus, ServiceBusConfigurationLoader}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.util.control.Breaks._

trait Commands
case class InitCommand(hyperBus: HyperBus) extends Commands
case class InputCommand(message: String) extends Commands

@contentType("application/vnd+test-body.json")
case class TestBody(content: Option[String]) extends Body

@url("/test")
case class TestRequest(body: TestBody,
                       messageId: String = IdUtils.createId,
                       correlationId: Option[String] = MessagingContext.correlationId) extends StaticGet(body)
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
    val removeMember = """^remove (.+)://(.+)@(.+):(\d+)$""".r

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

        case removeMember(protocol,system,host,port) ⇒
          ActorSystemRegistry.get("eu-inn").map { actorSystem ⇒
            val cluster = Cluster(actorSystem)
            val address = Address(protocol,system,host,port.toInt)
            out("Removing: " + address)
            cluster.down(address)
          } getOrElse {
            out("Can't find eu-inn actorsystem")
          }

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
      eu.inn.hyperbus.impl.Helpers.decodeDynamicRequest(RequestHeader(url, method, contentType, IdUtils.createId, None), jp)
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
