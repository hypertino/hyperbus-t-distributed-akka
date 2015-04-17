import eu.inn.binders.annotations.fieldName
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.HyperBus
import eu.inn.servicebus.transport.InprocTransport
import eu.inn.servicebus.ServiceBus
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, FreeSpec}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class TestBody1(resourceData: String) extends Body {
  override def contentType = Some("application/vnd+test-1.json")
}

case class TestBody2(resourceData: Long) extends Body {
  override def contentType = Some("application/vnd+test-2.json")
}

case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Body.LinksMap = Map(
                             StandardLink.LOCATION -> Left(Link("/resources/{resourceId}", templated = Some(true)))))
  extends CreatedBody with NoContentType



case class TestPost1(initBody: TestBody1) extends Post(initBody)
with DefinedResponse[Created[TestCreatedBody]] {
  override def url = "/resources"
}

case class TestPost2(initBody: TestBody2) extends Post(initBody)
with DefinedResponse[Created[TestCreatedBody]] {
  override def url = "/resources"
}

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    "Send and Receive" in {
      val tr = new InprocTransport
      val hyperBus = new HyperBus(new ServiceBus(tr,tr))

      hyperBus.subscribe(None, { post: TestPost1 =>
        Future {
          new Created(TestCreatedBody("100500"))
        }
      })

      val f = hyperBus.send(TestPost1(TestBody1("ha ha")))

      whenReady(f) { r =>
        //r should (new Created(TestCreatedBody("100500")))
        r.body should equal(TestCreatedBody("100500"))
      }
    }
  }
}
