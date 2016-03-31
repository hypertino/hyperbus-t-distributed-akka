import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.{Null, Text}
import eu.inn.hyperbus.Hyperbus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.transport._
import eu.inn.hyperbus.transport.api.matchers.{Any, RequestMatcher}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.transport.api._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import testclasses._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class HyperbusInprocTest extends FreeSpec with ScalaFutures with Matchers {
  "Hyperbus " - {
    "Send and Receive" in {

      val hyperbus = newHyperbus()

      hyperbus ~> { implicit post: testclasses.TestPost1 =>
        Future {
          Created(testclasses.TestCreatedBody("100500"))
        }
      }

      hyperbus ~> { implicit post: TestPost2 =>
        Future {
          Created(testclasses.TestCreatedBody(post.body.resourceData.toString))
        }
      }

      hyperbus ~> { implicit put: TestPostWith2Responses =>
        Future {
          Ok(TestAnotherBody(put.body.resourceData.reverse))
        }
      }

      val f1 = hyperbus <~ testclasses.TestPost1(testclasses.TestBody1("ha ha"))
      whenReady(f1) { r =>
        r.body should equal(testclasses.TestCreatedBody("100500"))
      }

      val f2 = hyperbus <~ TestPost2(TestBody2(7890))
      whenReady(f2) { r =>
        r.body should equal(testclasses.TestCreatedBody("7890"))
      }

      val f3 = hyperbus <~ TestPostWith2Responses(testclasses.TestBody1("Yey"))
      whenReady(f3) { r =>
        r.body should equal(TestAnotherBody("yeY"))
      }

      val f4 = hyperbus <~ SomeContentPut("/test", DynamicBody(Null)) // this should just compile, don't remove
      intercept[NoTransportRouteException] {
        Await.result(f4, 10.seconds)
      }
    }

    "Send and Receive multiple responses" in {
      val hyperbus = newHyperbus()

      hyperbus ~> { post: TestPost3 =>
        Future {
          if (post.body.resourceData == 1)
            Created(testclasses.TestCreatedBody("100500"))
          else if (post.body.resourceData == -1)
            throw Conflict(ErrorBody("failed"))
          else if (post.body.resourceData == -2)
            Conflict(ErrorBody("failed"))
          else
            Ok(DynamicBody(Text("another result")))
        }
      }

      val f = hyperbus <~ TestPost3(TestBody2(1))

      whenReady(f) { r =>
        r shouldBe a[Created[_]]
        r.body should equal(testclasses.TestCreatedBody("100500"))
      }

      val f2 = hyperbus <~ TestPost3(TestBody2(2))

      whenReady(f2) { r =>
        r shouldBe a[Ok[_]]
        r.body should equal(DynamicBody(Text("another result")))
      }

      val f3 = hyperbus <~ TestPost3(TestBody2(-1))

      whenReady(f3.failed) { r =>
        r shouldBe a[Conflict[_]]
      }

      val f4 = hyperbus <~ TestPost3(TestBody2(-2))

      whenReady(f4.failed) { r =>
        r shouldBe a[Conflict[_]]
      }
    }
  }

  def newHyperbus() = {
    val tr = new InprocTransport
    val cr = List(TransportRoute[ClientTransport](tr, RequestMatcher(Some(Uri(Any)))))
    val sr = List(TransportRoute[ServerTransport](tr, RequestMatcher(Some(Uri(Any)))))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new Hyperbus(transportManager, logMessages = true)
  }
}
