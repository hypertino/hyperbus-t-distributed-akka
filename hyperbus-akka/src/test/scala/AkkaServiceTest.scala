
import akka.actor.{Actor, ActorSystem}
import akka.testkit.TestActorRef
import akka.util.Timeout
import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.{Null, Text}
import eu.inn.hyperbus.transport.api.uri.{Uri,AnyValue}
import eu.inn.hyperbus.{HyperBus, IdGenerator}
import eu.inn.hyperbus.akkaservice.annotations.group
import eu.inn.hyperbus.akkaservice.{AkkaHyperService, _}
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.transport._
import eu.inn.hyperbus.transport.api._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@body("application/vnd+test-1.json")
case class TestBody1(resourceData: String) extends Body

@body("application/vnd+test-2.json")
case class TestBody2(resourceData: Long) extends Body

@body("application/vnd+created-body.json")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: LinksMap.LinksMapType = LinksMap("/resources/{resourceId}"))
  extends CreatedBody

// with NoContentType

@body("application/vnd+test-error-body.json")
case class TestErrorBody(code: String,
                         description: Option[String] = None,
                         errorId: String = IdGenerator.create()) extends ErrorBody {
  def message = code + description.map(": " + _).getOrElse("")

  def extra = Null
}


@request("/resources")
case class TestPost1(body: TestBody1) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@request("/resources")
case class TestPost2(body: TestBody2) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@request("/resources")
case class TestPost3(body: TestBody2) extends StaticPost(body)
with DefinedResponse[(Ok[DynamicBody], Created[TestCreatedBody], NotFound[TestErrorBody])]

class TestActor extends Actor {
  var count = 0

  def receive = AkkaHyperService.dispatch(this)

  def ~>(implicit testPost1: TestPost1) = {
    count += 1
    Future {
      Created(TestCreatedBody("100500"))
    }
  }

  def ~>(testPost3: TestPost3) = {
    count += 1
    Future {
      if (testPost3.body.resourceData == 1)
        Created(TestCreatedBody("100500"), headers = Map.empty, messageId = "123", correlationId = "123")
      else
      if (testPost3.body.resourceData == -1)
        throw Conflict(ErrorBody("failed"))
      else
      if (testPost3.body.resourceData == -2)
        Conflict(ErrorBody("failed"))
      else
      if (testPost3.body.resourceData == -3)
        NotFound(TestErrorBody("not_found"))
      else
        Ok(DynamicBody(Text("another result")), headers = Map.empty, messageId = "123", correlationId = "123")
    }
  }
}

class TestGroupActor extends Actor {
  var count = 0

  def receive = AkkaHyperService.dispatch(this)

  @group("group1")
  def |>(testPost1: TestPost1) = {
    count += 1
    Future.successful {}
  }
}

class AkkaHyperServiceTest extends FreeSpec with ScalaFutures with Matchers {

  def newHyperBus = {
    val tr = new InprocTransport
    val cr = List(TransportRoute[ClientTransport](tr, Uri(AnyValue)))
    val sr = List(TransportRoute[ServerTransport](tr, Uri(AnyValue)))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(transportManager)
  }

  "AkkaHyperService " - {
    "Send and Receive" in {
      implicit lazy val system = ActorSystem()
      val hyperBus = newHyperBus
      val actorRef = TestActorRef[TestActor]
      val groupActorRef = TestActorRef[TestGroupActor]

      implicit val timeout = Timeout(20.seconds)
      hyperBus.routeTo[TestActor](actorRef)
      hyperBus.routeTo[TestGroupActor](groupActorRef)

      val f1 = hyperBus <~ TestPost1(TestBody1("ha ha"), headers = Map.empty, messageId = "abc", correlationId = "xyz")

      whenReady(f1) { r =>
        //r.messageId should equal("123")
        r.correlationId should equal("xyz")
        r.body should equal(TestCreatedBody("100500"))
        actorRef.underlyingActor.count should equal(1)
        groupActorRef.underlyingActor.count should equal(1)
      }

      val f2 = hyperBus <| TestPost1(TestBody1("ha ha"))

      whenReady(f2) { r =>
        actorRef.underlyingActor.count should equal(2)
        groupActorRef.underlyingActor.count should equal(2)
      }
      Await.result(system.terminate(), 10.second)
    }

    "Send and Receive multiple responses" in {
      implicit lazy val system = ActorSystem()
      val hyperBus = newHyperBus
      val actorRef = TestActorRef[TestActor]
      implicit val timeout = Timeout(20.seconds)
      hyperBus.routeTo[TestActor](actorRef)

      val f = hyperBus <~ TestPost3(TestBody2(1))

      whenReady(f) { r =>
        r should equal(Created(TestCreatedBody("100500"), headers = Map.empty, messageId = "123", correlationId = "123"))
      }

      val f2 = hyperBus <~ TestPost3(TestBody2(2))

      whenReady(f2) { r =>
        r should equal(Ok(DynamicBody(Text("another result")), headers = Map.empty, messageId = "123", correlationId = "123"))
      }

      val f3 = hyperBus <~ TestPost3(TestBody2(-1))

      whenReady(f3.failed) { r =>
        r shouldBe a[Conflict[_]]
      }

      val f4 = hyperBus <~ TestPost3(TestBody2(-2))

      whenReady(f4.failed) { r =>
        r shouldBe a[Conflict[_]]
      }

      val f5 = hyperBus <~ TestPost3(TestBody2(-3))

      whenReady(f5.failed) { r =>
        r shouldBe a[NotFound[_]]
        r.asInstanceOf[Response[_]].body shouldBe a[TestErrorBody]
      }
      Await.result(system.terminate(), 10.second)
    }
  }
}
