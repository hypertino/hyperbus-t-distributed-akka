import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest.{Link, _}
import eu.inn.hyperbus.rest.annotations.{contentType, url}
import eu.inn.hyperbus.rest.standard._
import eu.inn.servicebus.{TransportRoute, ServiceBus}
import eu.inn.servicebus.transport.{ServerTransport, AnyArg, ClientTransport, InprocTransport}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@contentType("application/vnd+test-1.json")
case class TestBody1(resourceData: String) extends Body

@contentType("application/vnd+test-2.json")
case class TestBody2(resourceData: Long) extends Body

@contentType("application/vnd+created-body.json")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Body.LinksMap = Map(
                             DefLink.LOCATION -> Left(Link("/resources/{resourceId}", templated = Some(true)))))
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
  |[Ok[DynamicBody], |[Created[TestCreatedBody], !]]
  ]

@url("/empty")
case class TestPost4(body: TestBody1) extends StaticPost(body)
with DefinedResponse[NoContent[EmptyBody]]

class HyperBusInprocTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    "Send and Receive" in {

      val hyperBus = newHyperBus()

      hyperBus ~> { post: TestPost1 =>
        Future {
          new Created(TestCreatedBody("100500"))
        }
      }

      val f = hyperBus <~ TestPost1(TestBody1("ha ha"))

      whenReady(f) { r =>
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
            throw new Conflict(ErrorBody("failed"))
          else
          if (post.body.resourceData == -2)
            Conflict(ErrorBody("failed"))
          else
            Ok(DynamicBody(Text("another result")))
        }
      }

      val f = hyperBus <~ TestPost3(TestBody2(1))

      whenReady(f) { r =>
        r should equal(Created(TestCreatedBody("100500")))
      }

      val f2 = hyperBus <~ TestPost3(TestBody2(2))

      whenReady(f2) { r =>
        r should equal(Ok(DynamicBody(Text("another result"))))
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
    val cr = List(TransportRoute[ClientTransport](tr, AnyArg))
    val sr = List(TransportRoute[ServerTransport](tr, AnyArg))
    val serviceBus = new ServiceBus(cr, sr)
    new HyperBus(serviceBus)
  }
}
