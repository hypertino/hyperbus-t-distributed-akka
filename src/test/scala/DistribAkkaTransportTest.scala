import java.io.Reader
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive
import akka.testkit.TestActorRef
import com.hypertino.binders.value.Obj
import com.hypertino.hyperbus.model.annotations.{body, request, response}
import com.hypertino.hyperbus.model.{Body, MessagingContext, Method, Request, Response, ResponseBase, ResponseHeaders, ResponseMeta}
import com.hypertino.hyperbus.serialization.{RequestDeserializer, ResponseBaseDeserializer}
import com.hypertino.hyperbus.transport.api._
import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher
import com.hypertino.hyperbus.transport.distributedakka.ActorSystemInjector
import com.typesafe.config.ConfigFactory
import monix.execution.Ack.Continue
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}
import scaldi.Module

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Success



@body("mock")
case class MockBody(test: String) extends Body

@request(Method.POST, "hb://mock")
case class MockRequest(body: MockBody) extends Request[MockBody]

@request(Method.POST, "hb://not-existing")
case class MockNotExistingRequest(body: MockBody) extends Request[MockBody]

@response(200)
case class MockResponse[B <: MockBody](body: B) extends Response[B]

object MockResponse

class TestActorX extends Actor with ActorLogging {
  val membersUp = new AtomicInteger(0)
  val memberUpPromise = Promise[Unit]()
  val memberUpFuture: Future[Unit] = memberUpPromise.future

  override def receive: Receive = LoggingReceive {
    case MemberUp(member) => {
      membersUp.incrementAndGet()
      memberUpPromise.success({})
      log.info("Member is ready!")
    }
  }
}

class DistribAkkaTransportTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter {
  //implicit var actorSystem: ActorSystem = null

  var transportManager: TransportManager = null
  before {
    implicit val injector = new Module {
      bind [Scheduler] to global
      bind [ActorSystem] identifiedBy "com-hypertino" to ActorSystem("com-hypertino")
    }
    val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load(), injector)
    transportManager = new TransportManager(transportConfiguration)(injector)
    implicit val actorSystem = ActorSystemInjector()
    val testActor = TestActorRef[TestActorX]
    Cluster(actorSystem).subscribe(testActor, initialStateMode = InitialStateAsEvents, classOf[MemberEvent])
  }

  after {
    if (transportManager != null) {
      Await.result(transportManager.shutdown(20.seconds) runAsync, 20.seconds)
    }
  }

  "DistributedAkkaTransport " - {
    "Send and Receive" in {
      val cnt = new AtomicInteger(0)
      implicit val mcx = MessagingContext("123")
      implicit val patienceConfig = PatienceConfig(scaled(Span(5, Seconds)))

      val responseDeserializer : ResponseBaseDeserializer = (reader: Reader, obj: Obj) ⇒ {
        MockResponse(MockBody(reader, ResponseHeaders(obj).contentType))
      }

      val requestDeserializer: RequestDeserializer[MockRequest] = MockRequest.apply(_: Reader, _: Obj)

      val c1 = transportManager.commands[MockRequest](RequestMatcher("hb://mock", Method.POST), requestDeserializer).subscribe { c ⇒
        c.reply(Success {
          cnt.incrementAndGet()
          MockResponse(MockBody(c.request.body.test.reverse))
        })
        Continue
      }

      val c2 = transportManager.commands[MockRequest](RequestMatcher("hb://mock", Method.POST), requestDeserializer).subscribe { c ⇒
        c.reply(Success {
          cnt.incrementAndGet()
          MockResponse(MockBody(c.request.body.test.reverse))
        })
        Continue
      }

      val e1 = transportManager.events[MockRequest](RequestMatcher("hb://mock", Method.POST), "sub1", requestDeserializer).subscribe { event ⇒
        event.body.test should equal("12345")
        cnt.incrementAndGet()
        Continue
      }

      val e2 = transportManager.events[MockRequest](RequestMatcher("hb://mock", Method.POST), "sub1", requestDeserializer).subscribe { event ⇒
        event.body.test should equal("12345")
        cnt.incrementAndGet()
        Continue
      }

      val e3 = transportManager.events[MockRequest](RequestMatcher("hb://mock", Method.POST), "sub2", requestDeserializer).subscribe { event ⇒
        event.body.test should equal("12345")
        cnt.incrementAndGet()
        Continue
      }

      Thread.sleep(500) // we need to wait until subscriptions will go across

      val f: Future[ResponseBase] = transportManager.ask(MockRequest(MockBody("12345")), responseDeserializer) runAsync

      val msg = f.futureValue

      msg shouldBe a[MockResponse[_]]
      msg.asInstanceOf[MockResponse[MockBody]].body.test should equal("54321")
      Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
      cnt.get should equal(3)

      c1.cancel()
      c2.cancel()
      e1.cancel()
      e2.cancel()
      e3.cancel()

      val f2: Future[ResponseBase] = transportManager.ask(MockRequest(MockBody("12345")), responseDeserializer) runAsync

      /*
      Thread.sleep(6000)
      todo: this is not working for some reason, route isn't deleted after unsubscribe?
      whenReady(f2.failed, timeout(Span(10, Seconds))) { e =>
        e shouldBe a[NoTransportRouteException]
      }
      */

      // Thread.sleep(500) // we need to wait until subscriptions will go across

      val f3: Future[ResponseBase] = transportManager.ask(MockNotExistingRequest(MockBody("12345")), responseDeserializer) runAsync

      whenReady(f3.failed, timeout(Span(1, Seconds))) { e =>
        e shouldBe a[NoTransportRouteException]
      }
    }

    /*"Dispatcher test" in {
      val cnt = new AtomicInteger(0)

      val id = serviceBus.process[String, String](Topic("/topic/{abc}", PartitionArgs(Map.empty)),
        mockDecoder, mockExtractor[String], null) { s =>
        cnt.incrementAndGet()
        Thread.sleep(15000)
        mockResult(s.reverse)
      }

      val futures = 0 to 300 map { _ ⇒
        serviceBus.ask[String, String](Topic("/topic/{abc}", PartitionArgs(Map.empty)),
          "12345",
          mockEncoder, mockDecoder)
      }
      import scala.concurrent.ExecutionContext.Implicits.global
      val f = Future.sequence(futures)

      val result = Await.ready(f, 120.seconds)
      println(result)
      /*whenReady(f) { s =>
        s should equal("54321")
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(100)
      }*/
    }*/
  }
}
