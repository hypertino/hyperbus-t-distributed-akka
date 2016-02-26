import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.{Body, Method, Request}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@body("mock")
case class MockBody(test: String) extends Body

@request(Method.POST, "/mock/{partitionId}")
case class MockRequest(partitionId: String, body: MockBody) extends Request[MockBody]

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

      transportManager.onEvent(RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub1", MockRequest.apply) {
        case msg: MockRequest => Future {
          msg.body.test should equal("12345")
          cnt.incrementAndGet()
        }
      }

      transportManager.onEvent(RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub1", MockRequest.apply) {
        case msg: MockRequest => Future {
          msg.body.test should equal("12345")
          cnt.incrementAndGet()
        }
      }

      transportManager.onEvent(RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub2", MockRequest.apply) {
        case msg: MockRequest => Future {
          msg.body.test should equal("12345")
          cnt.incrementAndGet()
        }
      }

      Thread.sleep(1000) // we need to wait until subscriptions will go acros the
      // clear counter
      cnt.set(0)
      val f = Future.sequence(List(
        transportManager.publish(MockRequest("1", MockBody("12345"))),
        transportManager.publish(MockRequest("2", MockBody("12345"))))
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

