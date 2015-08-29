import java.io.{InputStream, OutputStream}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.binders._
import eu.inn.binders.json.SerializerFactory
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import eu.inn.servicebus.transport.config.TransportConfigurationLoader
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await, Future}

class KafkaTransportTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter {
  var serviceBus: TransportManager = null
  before {
    val serviceBusConfig = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
    serviceBus = new TransportManager(serviceBusConfig)
  }

  after {
    if (serviceBus != null) {
      Await.result(serviceBus.shutdown(10.seconds), 10.seconds)
    }
  }

  "KafkaTransport " - {
    "Publish and Subscribe" in {
      import ExecutionContext.Implicits.global
      val cnt = new AtomicInteger(0)

      serviceBus.subscribe(Topic("/topic/{abc}"), "sub1",
        MockRequestDecoder) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      serviceBus.subscribe(Topic("/topic/{abc}"), "sub1",
        MockRequestDecoder) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      serviceBus.subscribe(Topic("/topic/{abc}"), "sub2",
        MockRequestDecoder) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      Thread.sleep(1000) // we need to wait until subscriptions will go acros the
      // clear counter
      cnt.set(0)
      val f: Future[Unit] = serviceBus.publish(MockRequest("/topic/{abc}", "12345"))

      whenReady(f) { _ =>
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(2)
      }
    }
  }
}

// move mocks to separate assembly
case class MockRequest(specificTopic: String, message: String,
                       correlationId: String = UUID.randomUUID().toString,
                       messageId: String = UUID.randomUUID().toString) extends TransportRequest {
  def topic: Topic = Topic(specificTopic)
  override def encode(output: OutputStream): Unit = {
    SerializerFactory.findFactory().withStreamGenerator(output)(_.bind(this))
  }
}

case class MockResponse(message: String,
                        correlationId: String = UUID.randomUUID().toString,
                        messageId: String = UUID.randomUUID().toString) extends TransportResponse {
  override def encode(output: OutputStream): Unit = {
    SerializerFactory.findFactory().withStreamGenerator(output)(_.bind(this))
  }
}

object MockRequestDecoder extends Decoder[MockRequest] {
  override def apply(input: InputStream): MockRequest = {
    SerializerFactory.findFactory().withStreamParser(input)(_.unbind[MockRequest])
  }
}

object MockResponseDecoder extends Decoder[MockResponse] {
  override def apply(input: InputStream): MockResponse = {
    SerializerFactory.findFactory().withStreamParser(input)(_.unbind[MockResponse])
  }
}