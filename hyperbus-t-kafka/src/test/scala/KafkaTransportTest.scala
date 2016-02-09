import java.io.{InputStream, OutputStream}
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.binders._
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri.{UriParts, Uri}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class KafkaTransportTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter {
  var transportManager: TransportManager = null
  before {
    val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
    transportManager = new TransportManager(transportConfiguration)
  }

  after {
    if (transportManager != null) {
      Await.result(transportManager.shutdown(10.seconds), 10.seconds)
    }
  }

  "KafkaTransport " - {
    "Publish and Subscribe" in {

      import ExecutionContext.Implicits.global
      val cnt = new AtomicInteger(0)

      transportManager.subscribe(Uri("/topic/{abc}"), "sub1",
        MockRequestDeserializer) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      transportManager.subscribe(Uri("/topic/{abc}"), "sub1",
        MockRequestDeserializer) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      transportManager.subscribe(Uri("/topic/{abc}"), "sub2",
        MockRequestDeserializer) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      Thread.sleep(1000) // we need to wait until subscriptions will go acros the
      // clear counter
      cnt.set(0)
      val f = Future.sequence (List(
        transportManager.publish(MockRequest("/topic/{abc}", "1", "12345")),
        transportManager.publish(MockRequest("/topic/{abc}", "2", "12345")))
      )

      whenReady(f) { publishResults =>
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        publishResults.foreach { publishResult ⇒
          publishResult.sent should equal(Some(true))
          publishResult.offset shouldNot equal(None)
        }
        cnt.get should equal(4)
      }
    }
  }
}

// move mocks to separate assembly
case class MockRequest(uriPattern: String,
                       partitionId: String,
                       message: String,
                       headers: Map[String, Seq[String]] = Map.empty,
                       correlationId: String = IdGenerator.create(),
                       messageId: String = IdGenerator.create()) extends TransportRequest {
  def uri: Uri = Uri(uriPattern, Map("partitionId" → partitionId))

  override def serialize(output: OutputStream): Unit = {
    SerializerFactory.findFactory().withStreamGenerator(output)(_.bind(this))
  }
}

case class MockResponse(message: String,
                        partitionId: String,
                        headers: Map[String, Seq[String]] = Map.empty,
                        correlationId: String = IdGenerator.create(),
                        messageId: String = IdGenerator.create()) extends TransportResponse {
  override def serialize(output: OutputStream): Unit = {
    SerializerFactory.findFactory().withStreamGenerator(output)(_.bind(this))
  }
}

object MockRequestDeserializer extends Deserializer[MockRequest] {
  override def apply(input: InputStream): MockRequest = {
    SerializerFactory.findFactory().withStreamParser(input)(_.unbind[MockRequest])
  }
}

object MockResponseDeserializer extends Deserializer[MockResponse] {
  override def apply(input: InputStream): MockResponse = {
    SerializerFactory.findFactory().withStreamParser(input)(_.unbind[MockResponse])
  }
}