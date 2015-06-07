import java.io.{InputStream, ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import eu.inn.servicebus.{ServiceBusConfigurationLoader, TransportRoute, ServiceBus}
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DistribAkkaTransportTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter {
  var actorSystem: ActorSystem = null

  before {
    //actorSystem = getOrCreate()
  }

  after {
    ActorSystemRegistry.get("eu-inn") foreach { actorSystem ⇒
      actorSystem.shutdown()
      actorSystem.awaitTermination()
    }
  }

  "DistributedAkkaTransport " - {
    "Send and Receive" in {
      val serviceBusConfig = ServiceBusConfigurationLoader.fromConfig(ConfigFactory.load())
      val serviceBus = new ServiceBus(serviceBusConfig)
      val cnt = new AtomicInteger(0)

      val id = serviceBus.on[String, String](Topic("/topic/{abc}", PartitionArgs(Map())),
        mockDecoder, mockExtractor[String], null) { s =>
        cnt.incrementAndGet()
        mockResult(s.reverse)
      }


      val id2 = serviceBus.on[String, String](Topic("/topic/{abc}", PartitionArgs(Map())),
        mockDecoder,
        mockExtractor[String], null){ s =>
        cnt.incrementAndGet()
        mockResult(s.reverse)
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map())), "sub1",
        mockDecoder,
        mockExtractor[String]) { s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map())), "sub1",
        mockDecoder,
        mockExtractor[String]){ s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map())), "sub2",
        mockDecoder,
        mockExtractor[String]){ s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      val f: Future[String] = serviceBus.ask[String, String](Topic("/topic/{abc}", PartitionArgs(Map())),
        "12345",
        mockEncoder, mockDecoder)

      whenReady(f) { s =>
        s should equal("54321")
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(3)
        /*
        serviceBus.off(id)

        todo: NoTransportRouteException doesn't work for DistribPubSub
        val f2: Future[String] = serviceBus.ask[String, Int](Topic("topic", PartitionArgs(Map())), 1)
        whenReady(f2.failed, timeout(Span(10, Seconds))) { e =>
          e shouldBe a[NoTransportRouteException]
        }*/
      }
    }
  }

  def mockExtractor[T]: PartitionArgsExtractor[T] = {
    (x: T) => PartitionArgs(Map())
  }

  def mockEncoder(in: String, out: OutputStream) = {
    out.write(in.getBytes("UTF-8"))
  }

  def mockDecoder(in: InputStream): String = {
    IOUtils.toString(in, "UTF-8")
  }

  def mockResult(result: String): SubscriptionHandlerResult[String] = {
    SubscriptionHandlerResult[String](Future.successful(result), mockEncoder)
  }

  def mockResultU: SubscriptionHandlerResult[Unit] = {
    SubscriptionHandlerResult[Unit](Future.successful{}, null)
  }
}
