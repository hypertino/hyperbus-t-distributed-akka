import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.{Link, _}
import eu.inn.hyperbus.transport._
import eu.inn.hyperbus.transport.api.matchers.{Any, RequestMatcher}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.transport.api.{ClientTransport, ServerTransport, TransportManager, TransportRoute}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@body("test-1")
case class TestBody1(resourceData: String) extends Body

@body("test-2")
case class TestBody2(resourceData: Long) extends Body

@body("created-body")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Links.LinksMap = Links.location("/resources/{resourceId}", templated = true))
  extends CreatedBody

@body
case class TestBodyNoContentType(resourceData: String) extends Body

@request(Method.POST, "/resources")
case class TestPost1(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[Created[TestCreatedBody]]

@request(Method.POST, "/resources")
case class TestPost2(body: TestBody2) extends Request[TestBody2]
  with DefinedResponse[Created[TestCreatedBody]]

@request(Method.POST, "/resources")
case class TestPost3(body: TestBody2) extends Request[TestBody2]
  with DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody])]

@request(Method.POST, "/empty")
case class TestPostWithNoContent(body: TestBody1) extends Request[TestBody1]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.POST, "/empty")
case class StaticPostWithDynamicBody(body: DynamicBody) extends Request[DynamicBody]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.POST, "/empty")
case class StaticPostWithEmptyBody(body: EmptyBody) extends Request[EmptyBody]
  with DefinedResponse[NoContent[EmptyBody]]

@request(Method.GET, "/empty")
case class StaticGetWithQuery(body: QueryBody) extends Request[QueryBody]
  with DefinedResponse[Ok[DynamicBody]]

@request(Method.POST, "/content-body-not-specified")
case class StaticPostBodyWithoutContentType(body: TestBodyNoContentType) extends Request[TestBodyNoContentType]
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

      hyperBus ~> { implicit post: TestPost2 =>
        Future {
          Created(TestCreatedBody(post.body.resourceData.toString))
        }
      }

      val f1 = hyperBus <~ TestPost1(TestBody1("ha ha"))
      whenReady(f1) { r =>
        r.body should equal(TestCreatedBody("100500"))
      }

      val f2 = hyperBus <~ TestPost2(TestBody2(7890))
      whenReady(f2) { r =>
        r.body should equal(TestCreatedBody("7890"))
      }
    }

    "Send and Receive multiple responses" in {
      val hyperBus = newHyperBus()

      hyperBus ~> { post: TestPost3 =>
        Future {
          if (post.body.resourceData == 1)
            Created(TestCreatedBody("100500"))
          else if (post.body.resourceData == -1)
            throw Conflict(ErrorBody("failed"))
          else if (post.body.resourceData == -2)
            Conflict(ErrorBody("failed"))
          else
            Ok(DynamicBody(Text("another result")))
        }
      }

      val f = hyperBus <~ TestPost3(TestBody2(1))

      whenReady(f) { r =>
        r shouldBe a[Created[_]]
        r.body should equal(TestCreatedBody("100500"))
      }

      val f2 = hyperBus <~ TestPost3(TestBody2(2))

      whenReady(f2) { r =>
        r shouldBe a[Ok[_]]
        r.body should equal(DynamicBody(Text("another result")))
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
    val cr = List(TransportRoute[ClientTransport](tr, RequestMatcher(Some(Uri(Any)))))
    val sr = List(TransportRoute[ServerTransport](tr, RequestMatcher(Some(Uri(Any)))))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(transportManager, logMessages = true)
  }
}
