package eu.inn.hyperbus.serialization

import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.{HyperBus, ServiceBus}
import eu.inn.hyperbus.transport.InprocTransport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, FreeSpec}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TestBody extends Body with ContentType

case class TestBody1(resourceData: String) extends TestBody {
  def contentType = "application/vnd+test-1.json"
}

case class TestBody2(resourceData: Long) extends TestBody {
  def contentType = "application/vnd+test-2.json"
}

case class TestCreatedBody(resourceId: String) extends CreatedResponseBody(
  Link("/resources/{resourceId}", templated = Some(true))) {
}

case class TestPost1(initBody: TestBody1) extends Post(initBody)
with DefinedResponse[Created[TestCreatedBody]] {
  override def uri = "/resources"
}

case class TestPost2(initBody: TestBody2) extends Post(initBody)
with DefinedResponse[Created[TestCreatedBody]] {
  override def uri = "/resources"
}

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    "Send and Receive" in {
      val tr = new InprocTransport
      val hyperBus = new HyperBus(new ServiceBus(tr,tr))

      hyperBus.subscribe(None, { post: TestPost1 =>
        Future {
          TestCreatedBody("100500")
        }
      })

      val f = hyperBus.send(TestPost1(TestBody1("ha ha")))

      whenReady(f) { r =>
        r should equal (new Created(TestCreatedBody("ha ha")))
      }
    }
  }
}
