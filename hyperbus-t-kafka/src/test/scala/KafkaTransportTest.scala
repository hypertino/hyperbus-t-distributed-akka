import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.{Body, Method, Request}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Millis, Span}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@body("mock")
case class MockBody(test: String) extends Body

@request(Method.POST, "/mock/{partitionId}")
case class MockRequest(partitionId: String, body: MockBody) extends Request[MockBody]

class KafkaTransportTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter with Eventually {
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

  implicit val defaultPatience = PatienceConfig(timeout =  Span(5, Seconds), interval = Span(50, Millis))

  "KafkaTransport " - {
    "Publish and then Subscribe" in {

      import ExecutionContext.Implicits.global

      // read previous messages if any
      val fsub1 = transportManager.onEvent(RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub1", MockRequest.apply) {
        case msg ⇒ Future {}
      }
      val fsub2 = transportManager.onEvent(RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub2", MockRequest.apply) {
        case msg ⇒ Future {}
      }
      Thread.sleep(1000)
      val sub1 = fsub1.futureValue
      val sub2 = fsub2.futureValue

      transportManager.off(sub1).futureValue
      transportManager.off(sub2).futureValue

      Thread.sleep(1000)

      val f = Future.sequence(List(
        transportManager.publish(MockRequest("1", MockBody("12345"))),
        transportManager.publish(MockRequest("2", MockBody("12345"))))
      )
      val publishResults = f.futureValue
      publishResults.foreach { publishResult ⇒
        publishResult.sent should equal(Some(true))
        publishResult.offset shouldNot equal(None)
      }

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
      //cnt.set(0)


      eventually {
        cnt.get should equal(4)
      }
      Thread.sleep(1000) // give chance to increment to another service (in case of wrong implementation)
      cnt.get should equal(4)
    }
  }
}

