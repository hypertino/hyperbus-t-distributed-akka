package com.hypertino.hyperbus.cli

import akka.actor.Address
import akka.cluster.Cluster
import com.hypertino.binders.json.DefaultJsonBindersFactory
import com.hypertino.binders.value.Text
import com.hypertino.inflector.naming.PlainConverter
import com.hypertino.service.config.ConfigLoader
import com.hypertino.service.control.api.{Console, Service, ServiceController, ShutdownMonitor}
import com.hypertino.service.control.{ConsoleModule, ConsoleServiceController}
import com.typesafe.config.Config
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model._
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.serialization.StringDeserializer
import com.hypertino.hyperbus.transport.ActorSystemRegistry
import com.hypertino.hyperbus.transport.api.{TransportConfigurationLoader, TransportManager}
import scaldi.Injector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait Commands

case class InitCommand(hyperbus: Hyperbus) extends Commands

case class InputCommand(message: String) extends Commands

@body("test-body")
case class TestBody(content: Option[String]) extends Body

@request(Method.GET, "/test")
case class TestRequest(body: TestBody) extends Request[TestBody]
  with DefinedResponse[Ok[TestBody]]

class CliService(console: Console, config: Config) extends Service {
  console.writeln("Starting hyperbus-cli...")

  val transportConfiguration = TransportConfigurationLoader.fromConfig(config)
  val transportManager = new TransportManager(transportConfiguration)
  val hyperbus = new Hyperbus(transportManager, logMessages = true)

  val actorSystem = ActorSystemRegistry.get("com-hypertino").get

  hyperbus ~> { r: TestRequest ⇒
    Future.successful {
      Ok(DynamicBody(Text(s"Received: ${r.body.content}")))
    }
  }

  def ask(request: String): Unit = {
    val r = StringDeserializer.request[DynamicRequest](request)
    out(s"<~$r")
    val f = hyperbus <~ r
    printResponse(f)
  }

  def publish(request: String): Unit = {
    val r = StringDeserializer.request[DynamicRequest](request)
    out(s"<!$r")
    val f = hyperbus <| r
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
    console.writeln(s"<- ${r.getClass.getName}:{ ${r.statusCode} @ ${r.body.contentType}\n----------")
    outx(r.body)
    console.writeln("----------")
  }

  private def outx(r: Body): Unit = {
    import com.hypertino.binders.json.JsonBinders._
    implicit val defaultSerializerFactory = new DefaultJsonBindersFactory[PlainConverter.type](true)
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
      Await.result(hyperbus.shutdown(timeout), timeout)
    } catch {
      case t: Throwable ⇒
        console.writeln(t)
    }
  }
}

class CliServiceController(implicit injector: Injector)
  extends ConsoleServiceController {

  private val service = inject[CliService]

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
    console.writeln(
      s"""|Available commands: <~, <|, quit
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
  bind[Console] to injected[JLineConsole]
  bind[Config] to ConfigLoader()
  bind[CliService] to injected[CliService]
  bind[ServiceController] to injected[CliServiceController]

  def main(args: Array[String]): Unit = {
    inject[ServiceController].run()
  }
}
