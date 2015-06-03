import java.util.UUID

import akka.actor.{Actor, ActorSystem}
import akka.testkit.TestActorRef
import akka.util.Timeout
import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.akkaservice.{AkkaHyperService, _}
import eu.inn.hyperbus.akkaservice.annotations.group
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.annotations.{contentType, url}
import eu.inn.hyperbus.rest.standard._
import eu.inn.servicebus.{TransportRoute, ServiceBus}
import eu.inn.servicebus.transport.{ServerTransport, AnyArg, ClientTransport, InprocTransport}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

@contentType("application/vnd+test-1.json")
case class TestBody1(resourceData: String) extends Body

@contentType("application/vnd+test-2.json")
case class TestBody2(resourceData: Long) extends Body

@contentType("application/vnd+created-body.json")
case class TestCreatedBody(resourceId: String,
                           @fieldName("_links") links: Body.LinksMap = Map(
                             DefLink.LOCATION -> Left(Link("/resources/{resourceId}", templated = Some(true)))))
  extends CreatedBody with NoContentType

@contentType("application/vnd+test-error-body.json")
case class TestErrorBody(code: String,
                         description: Option[String] = None,
                         errorId: String = UUID.randomUUID().toString) extends ErrorBodyTrait {
  def message = code + description.map(": " + _).getOrElse("")
}


@url("/resources")
case class TestPost1(body: TestBody1) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@url("/resources")
case class TestPost2(body: TestBody2) extends StaticPost(body)
with DefinedResponse[Created[TestCreatedBody]]

@url("/resources")
case class TestPost3(body: TestBody2) extends StaticPost(body)
with DefinedResponse[
  |[Ok[DynamicBody], |[Created[TestCreatedBody], |[NotFound[TestErrorBody], !]]]
  ]

class TestActor extends Actor {
  var count = 0

  def receive = AkkaHyperService.dispatch(this)

  def on(testPost1: TestPost1) = {
    count += 1
    Future {
      Created(TestCreatedBody("100500"))
    }
  }

  def on(testPost3: TestPost3) = {
    count += 1
    Future {
      if (testPost3.body.resourceData == 1)
        Created(TestCreatedBody("100500"))
      else
      if (testPost3.body.resourceData == -1)
        throw new Conflict(ErrorBody("failed"))
      else
      if (testPost3.body.resourceData == -2)
        Conflict(ErrorBody("failed"))
      else
      if (testPost3.body.resourceData == -3)
        NotFound(TestErrorBody("not_found"))
      else
        Ok(DynamicBody(Text("another result")))
    }
  }
}

class TestGroupActor extends Actor {
  var count = 0

  def receive = AkkaHyperService.dispatch(this)

  @group("group1")
  def subscribe(testPost1: TestPost1) = {
    count += 1
    Future.successful {}
  }
}

class AkkaHyperServiceTest extends FreeSpec with ScalaFutures with Matchers {

  def newHyperBus = {
    val tr = new InprocTransport
    val cr = List(TransportRoute[ClientTransport](tr, AnyArg))
    val sr = List(TransportRoute[ServerTransport](tr, AnyArg))
    val serviceBus = new ServiceBus(cr, sr)
    new HyperBus(serviceBus)
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

      val f1 = hyperBus <~ TestPost1(TestBody1("ha ha"))

      whenReady(f1) { r =>
        r.body should equal(TestCreatedBody("100500"))
        actorRef.underlyingActor.count should equal(1)
        groupActorRef.underlyingActor.count should equal(1)
      }

      val f2 = hyperBus <| TestPost1(TestBody1("ha ha"))

      whenReady(f2) { r =>
        actorRef.underlyingActor.count should equal(2)
        groupActorRef.underlyingActor.count should equal(2)
      }
      system.shutdown()
    }

    "Send and Receive multiple responses" in {
      implicit lazy val system = ActorSystem()
      val hyperBus = newHyperBus
      val actorRef = TestActorRef[TestActor]
      implicit val timeout = Timeout(20.seconds)
      hyperBus.routeTo[TestActor](actorRef)

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

      val f5 = hyperBus <~ TestPost3(TestBody2(-3))

      whenReady(f5.failed) { r =>
        r shouldBe a[NotFound[_]]
        r.asInstanceOf[Response[_]].body shouldBe a[TestErrorBody]
      }
      system.shutdown()
    }
  }
}
