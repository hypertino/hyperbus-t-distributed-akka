import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest.{Link, _}
import eu.inn.hyperbus.rest.annotations.{body, request}
import eu.inn.hyperbus.rest.standard._
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@body("application/vnd+test-1.json")
case class TestBody1(resourceData: String) extends Body

@body("application/vnd+test-2.json")
case class TestBody2(resourceData: Long) extends Body

@body("application/vnd+created-body.json")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Links.Map = Map(
                             DefLink.LOCATION -> Left(Link("/resources/{resourceId}", templated = Some(true)))))
  extends CreatedBody// with NoContentType

@request("/resources")
case class TestPost1(body: TestBody1) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@request("/resources")
case class TestPost2(body: TestBody2) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@request("/resources")
case class TestPost3(body: TestBody2) extends StaticPost(body)
with DefinedResponse[
  |[Ok[DynamicBody], |[Created[TestCreatedBody], !]]
  ]

@request("/empty")
case class TestPostWithNoContent(body: TestBody1) extends StaticPost(body)
with DefinedResponse[NoContent[EmptyBody]]

@request("/empty")
case class StaticPostWithDynamicBody(body: DynamicBody) extends StaticPost(body)
with DefinedResponse[NoContent[EmptyBody]]

@request("/empty")
case class StaticPostWithEmptyBody(body: EmptyBody) extends StaticPost(body)
with DefinedResponse[NoContent[EmptyBody]]

class HyperBusInprocTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    "Send and Receive" in {

      val hyperBus = newHyperBus()

      hyperBus ~> { implicit post: TestPost1 =>
        Future {
          Created(TestCreatedBody("100500"))
        }
      }

      val f = hyperBus <~ TestPost1(TestBody1("ha ha"),
        messageId="abc",
        correlationId="xyz")

      whenReady(f) { r =>
        r.correlationId should equal("xyz")
        r.body should equal(TestCreatedBody("100500"))
      }
    }

    "Send and Receive multiple responses" in {
      val hyperBus = newHyperBus()

      hyperBus ~> { post: TestPost3 =>
        Future {
          if (post.body.resourceData == 1)
            Created(TestCreatedBody("100500"))
          else
          if (post.body.resourceData == -1)
            throw Conflict(ErrorBody("failed"))
          else
          if (post.body.resourceData == -2)
            Conflict(ErrorBody("failed"))
          else
            Ok(DynamicBody(Text("another result")))
        }
      }

      val f = hyperBus <~ TestPost3(TestBody2(1))

      whenReady(f) { r =>
        r should equal(Created(TestCreatedBody("100500"), messageId = r.messageId, correlationId = r.correlationId))
      }

      val f2 = hyperBus <~ TestPost3(TestBody2(2))

      whenReady(f2) { r =>
        r should equal(Ok(DynamicBody(Text("another result")), messageId = r.messageId, correlationId = r.correlationId))
      }

      val f3 = hyperBus <~ TestPost3(TestBody2(-1))

      whenReady(f3.failed) { r =>
        r shouldBe a[Conflict[_]]
      }

      val f4 = hyperBus <~ TestPost3(TestBody2(-2))

      whenReady(f4.failed) { r =>
        r shouldBe a[Conflict[_]]
      }
    }
  }

  def newHyperBus() = {
    val tr = new InprocTransport
    val cr = List(TransportRoute[ClientTransport](tr, AnyValue))
    val sr = List(TransportRoute[ServerTransport](tr, AnyValue))
    val serviceBus = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(serviceBus)
  }
}
