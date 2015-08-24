import java.io.{InputStream, OutputStream}
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import eu.inn.servicebus.{ServiceBus, ServiceBusConfigurationLoader}
import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


class KafkaTransportTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter {
  var serviceBus: ServiceBus = null
  before {
    val serviceBusConfig = ServiceBusConfigurationLoader.fromConfig(ConfigFactory.load())
    serviceBus = new ServiceBus(serviceBusConfig)
  }

  after {
    if (serviceBus != null) {
      Await.result(serviceBus.shutdown(10.seconds), 10.seconds)
    }
  }

  "KafkaTransport " - {
    "Publish and Subscribe" in {
      val cnt = new AtomicInteger(0)

      /*serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map.empty)), "sub1",
        mockDecoder,
        mockExtractor[String]) { s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map.empty)), "sub1",
        mockDecoder,
        mockExtractor[String]){ s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map.empty)), "sub2",
        mockDecoder,
        mockExtractor[String]){ s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }*/

      Thread.sleep(500) // we need to wait until subscriptions will go acros the

      val f: Future[Unit] = serviceBus.publish[String](Topic("/topic/{abc}", PartitionArgs(Map.empty)),
        "12345",
        mockEncoder)

      whenReady(f) { s =>
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(3)
      }
    }
  }

  def mockExtractor[T]: PartitionArgsExtractor[T] = {
    (x: T) => PartitionArgs(Map.empty)
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
