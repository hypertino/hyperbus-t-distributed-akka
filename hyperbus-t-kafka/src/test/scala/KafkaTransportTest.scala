import java.io.{InputStream, OutputStream}
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.binders._
import eu.inn.binders.json.SerializerFactory
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.transport.api._
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

      transportManager.subscribe(Topic("/topic/{abc}"), "sub1",
        MockRequestDecoder) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      transportManager.subscribe(Topic("/topic/{abc}"), "sub1",
        MockRequestDecoder) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      transportManager.subscribe(Topic("/topic/{abc}"), "sub2",
        MockRequestDecoder) { msg: MockRequest =>
        Future {
          msg.message should equal("12345")
          cnt.incrementAndGet()
        }
      }

      Thread.sleep(1000) // we need to wait until subscriptions will go acros the
      // clear counter
      cnt.set(0)
      val f: Future[PublishResult] = transportManager.publish(MockRequest("/topic/{abc}", "12345"))

      whenReady(f) { publishResult =>
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        publishResult.sent should equal(Some(true))
        publishResult.offset shouldNot equal(None)
        cnt.get should equal(2)
      }
    }
  }
}

// move mocks to separate assembly
case class MockRequest(specificTopic: String, message: String,
                       correlationId: String = IdGenerator.create(),
                       messageId: String = IdGenerator.create()) extends TransportRequest {
  def topic: Topic = Topic(specificTopic)
  override def encode(output: OutputStream): Unit = {
    SerializerFactory.findFactory().withStreamGenerator(output)(_.bind(this))
  }
}

case class MockResponse(message: String,
                        correlationId: String = IdGenerator.create(),
                        messageId: String = IdGenerator.create()) extends TransportResponse {
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