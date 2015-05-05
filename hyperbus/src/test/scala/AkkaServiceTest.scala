import akka.actor.{ActorSystem, Actor}
import akka.util.Timeout
import eu.inn.hyperbus.akkaservice.annotations.group
import scala.concurrent.duration._
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.akkaservice.AkkaHyperService
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.protocol.annotations.{url, contentType}
import eu.inn.servicebus.transport.InprocTransport
import eu.inn.servicebus.ServiceBus
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, FreeSpec}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import eu.inn.hyperbus.akkaservice._

import akka.testkit.TestActorRef

class TestActor extends Actor {
  def receive = AkkaHyperService.dispatch(this)

  def on(testPost1: TestPost1) = {
    Future {
      Created(TestCreatedBody("100500"))
    }
  }

  def on(testPost3: TestPost3) = {
    Future {
      if (testPost3.body.resourceData == 1)
        Created(TestCreatedBody("100500"))
      else
      if (testPost3.body.resourceData == -1)
        throw new ConflictError(Error("failed"))
      else
      if (testPost3.body.resourceData == -2)
        ConflictError(Error("failed"))
      else
        Ok(Dynamic(Text("another result")))
    }
  }
}

class TestGroupActor extends Actor {
  def receive = AkkaHyperService.dispatch(this)

  @group("group1")
  def on(testPost1: TestPost1) = {
    Future {
      println("received: " + testPost1)
      Ok(Empty())
    }
  }
}

class AkkaHyperServiceTest extends FreeSpec with ScalaFutures with Matchers{
  "AkkaHyperService " - {
    "Send and Receive" in {
      implicit lazy val system = ActorSystem()
      val tr = new InprocTransport
      val hyperBus = new HyperBus(new ServiceBus(tr,tr))
      val actorRef = TestActorRef[TestActor]
      val groupActorRef = TestActorRef[TestGroupActor]

      implicit val timeout = Timeout(20 seconds)
      hyperBus.routeTo[TestActor](actorRef)
      hyperBus.routeTo[TestGroupActor](groupActorRef)

      val f = hyperBus ? TestPost1(TestBody1("ha ha"))

      whenReady(f) { r =>
        r.body should equal(TestCreatedBody("100500"))
      }
      system.shutdown()
    }

    "Send and Receive multiple responses" in {
      implicit lazy val system = ActorSystem()
      val tr = new InprocTransport
      val hyperBus = new HyperBus(new ServiceBus(tr,tr))
      val actorRef = TestActorRef[TestActor]
      implicit val timeout = Timeout(20 seconds)
      hyperBus.routeTo[TestActor](actorRef)

      val f = hyperBus ? TestPost3(TestBody2(1))

      whenReady(f) { r =>
        r should equal(Created(TestCreatedBody("100500")))
      }

      val f2 = hyperBus ? TestPost3(TestBody2(2))

      whenReady(f2) { r =>
        r should equal(Ok(Dynamic(Text("another result"))))
      }

      val f3 = hyperBus ? TestPost3(TestBody2(-1))

      whenReady(f3.failed) { r =>
        r shouldBe a [ConflictError[Error]]
      }

      val f4 = hyperBus ? TestPost3(TestBody2(-2))

      whenReady(f4.failed) { r =>
        r shouldBe a [ConflictError[Error]]
      }
      system.shutdown()
    }
  }
}
