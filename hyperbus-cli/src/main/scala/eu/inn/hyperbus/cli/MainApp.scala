package eu.inn.hyperbus.cli

import akka.actor.Address
import akka.cluster.Cluster
import com.fasterxml.jackson.core.JsonFactory
import com.typesafe.config.Config
import eu.inn.binders.dynamic.Text
import eu.inn.binders.naming.PlainConverter
import eu.inn.config.ConfigLoader
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.{StringDeserializer, RequestHeader}
import eu.inn.hyperbus.transport.ActorSystemRegistry
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import eu.inn.hyperbus.{HyperBus, IdGenerator}
import eu.inn.servicecontrol.api.{Console, Service, ServiceController, ShutdownMonitor}
import eu.inn.servicecontrol.{ConsoleModule, ConsoleServiceController}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait Commands

case class InitCommand(hyperBus: HyperBus) extends Commands

case class InputCommand(message: String) extends Commands

@body("test-body")
case class TestBody(content: Option[String]) extends Body

@request("/test")
case class TestRequest(body: TestBody) extends StaticGet(body)
with DefinedResponse[Ok[TestBody]]

class CliService(console: Console, config: Config) extends Service {
  console.writeln("Starting hyperbus-cli...")

  val transportConfiguration = TransportConfigurationLoader.fromConfig(config)
  val transportManager = new TransportManager(transportConfiguration)
  val hyperBus = new HyperBus(transportManager)

  val actorSystem = ActorSystemRegistry.get("eu-inn").get

  hyperBus ~> { r: TestRequest ⇒
    Future.successful {
      Ok(DynamicBody(Text(s"Received: ${r.body.content}")))
    }
  }

  def ask(request: String): Unit = {
    val r = StringDeserializer.request[DynamicRequest](request)
    out(s"<~$r")
    val f = hyperBus <~ r
    printResponse(f)
  }

  def publish(request: String): Unit = {
    val r = StringDeserializer.request[DynamicRequest](request)
    out(s"<!$r")
    val f = hyperBus <| r
  }

  def leaveMember(protocol: String, system: String, host: String, port: String): Unit = {
    val cluster = Cluster(actorSystem)
    val address = Address(protocol, system, host, port.toInt)
    out("Leaving: " + address)
    cluster.leave(address)
  }

  def down(protocol: String, system: String, host: String, port: String): Unit = {
    val cluster = Cluster(actorSystem)
    val address = Address(protocol, system, host, port.toInt)
    out("Downing: " + address)
    cluster.down(address)
  }

  private def printResponse(response: Future[Response[Body]]) = {
    response map out recover {
      case x ⇒ out(x)
    }
    console.writeln()
  }

  private def out(s: Any): Unit = {
    s match {
      case r: Request[Body] ⇒ outx(r)
      case r: Response[Body] ⇒ outx(r)
      case _ ⇒
        console.writeln(s.toString)
    }
  }

  private def outx(r: Request[Body]): Unit = {
    console.writeln(s"-> ${r.getClass.getName}:{ ${r.method} ${r.uri} @ ${r.body.contentType}\n----------")
    outx(r.body)
    console.writeln("----------")
  }

  private def outx(r: Response[Body]): Unit = {
    console.writeln(s"<- ${r.getClass.getName}:{ ${r.status} @ ${r.body.contentType}\n----------")
    outx(r.body)
    console.writeln("----------")
  }

  private def outx(r: Body): Unit = {
    import eu.inn.binders.json._
    implicit val defaultSerializerFactory = new DefaultSerializerFactory[PlainConverter](true)
    r match {
      case d: DynamicBody ⇒
        console.writeln(d.content.toJson)
      case t ⇒
        console.writeln(t.toString)
    }
  }

  override def stopService(controlBreak: Boolean): Unit = {
    console.writeln("Exiting...")
    val timeout = 30.seconds
    try {
      Await.result(hyperBus.shutdown(timeout), timeout)
    } catch {
      case t: Throwable ⇒
        console.writeln(t)
    }
  }
}

class CliServiceController(service: CliService, console: Console, shutdownMonitor: ShutdownMonitor)
  extends ConsoleServiceController(service, console, shutdownMonitor) {

  val askCommand = """^<~\s*(.+)$""".r
  val publishCommand = """^\<|\s*(.+)$""".r
  val downMember = """^down (.+)://(.+)@(.+):(\d+)$""".r
  val leaveMember = """^leave (.+)://(.+)@(.+):(\d+)$""".r

  override def customCommand = {
    case askCommand(str) ⇒
      service.ask(str)

    case publishCommand(str) ⇒
      service.publish(str)

    case downMember(protocol, system, host, port) ⇒
      service.down(protocol, system, host, port)

    case leaveMember(protocol, system, host, port) ⇒
      service.leaveMember(protocol, system, host, port)
  }

  override def help(): Unit = {
    console.writeln(s"""|Available commands: <~, <|, quit
            |Example: <~ {"request":{"method":"get","uri":{"pattern":"/test"},"messageId":"123", "contentType": "test-body"},"body":{"content":"100500"}}""".stripMargin('|'))
  }
}

class JLineConsole extends Console {
  val consoleReader = new jline.console.ConsoleReader
  def inputIterator(): Iterator[Option[String]] = new Iterator[Option[String]] {
    var eof = false
    override def hasNext: Boolean = !eof
    override def next(): Option[String] = {
      consoleReader.accept()
      val s = consoleReader.readLine(">")
      if (s == null) {
        eof = true
        None
      } else {
        Some(s.trim)
      }
    }
  }

  def write(o: Any) = consoleReader.print(o.toString)

  def writeln(o: Any) = {
    consoleReader.println(o.toString)
    consoleReader.accept()
  }

  def writeln() = {
    consoleReader.println()
    consoleReader.accept()
  }
}

object MainApp extends ConsoleModule {
  bind [Console] to injected [JLineConsole]
  bind [Config] to ConfigLoader()
  bind [CliService] to injected [CliService]
  bind [ServiceController] to injected [CliServiceController]

  def main(args: Array[String]): Unit = {
    inject[ServiceController].run()
  }
}
