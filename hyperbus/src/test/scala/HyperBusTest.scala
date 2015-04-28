import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.protocol.annotations.{url, contentType}
import eu.inn.servicebus.transport.InprocTransport
import eu.inn.servicebus.ServiceBus
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, FreeSpec}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@contentType("application/vnd+test-1.json")
case class TestBody1(resourceData: String) extends Body

@contentType("application/vnd+test-2.json")
case class TestBody2(resourceData: Long) extends Body

@contentType("application/vnd+created-body.json")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Body.LinksMap = Map(
                             StandardLink.LOCATION -> Left(Link("/resources/{resourceId}", templated = Some(true)))))
  extends CreatedBody with NoContentType

@url("/resources")
case class TestPost1(body: TestBody1) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@url("/resources")
case class TestPost2(body: TestBody2) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@url("/resources")
case class TestPost3(body: TestBody2) extends StaticPost(body)
with DefinedResponse[
    | [Ok[DynamicBody], | [Created[TestCreatedBody], !]]
  ]

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    /*"Send and Receive" in {
      val tr = new InprocTransport
      val hyperBus = new HyperBus(new ServiceBus(tr,tr))

      hyperBus.subscribe[TestPost1](None) { post =>
        Future {
          new Created(TestCreatedBody("100500"))
        }
      }

      val f = hyperBus.send(TestPost1(TestBody1("ha ha")))

      whenReady(f) { r =>
        r.body should equal(TestCreatedBody("100500"))
      }
    }*/

    "Send and Receive multiple responses" in {
      val tr = new InprocTransport
      val hyperBus = new HyperBus(new ServiceBus(tr,tr))

      hyperBus.subscribe[TestPost3](None) { post =>
        Future {
          if (post.body.resourceData == 1)
            Created(TestCreatedBody("100500"))
          else
            Ok(DynamicBody(Text("another result")))
        }
      }

      /*val f = hyperBus.send(TestPost3(TestBody2(1)))

      whenReady(f) { r =>
        r should equal(Created(TestCreatedBody("100500")))
      }*/

      val f2 = hyperBus.send(TestPost3(TestBody2(2)))

      whenReady(f2) { r =>
        r should equal(Ok(DynamicBody(Text("another result"))))
      }
    }
  }
}
