import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive
import akka.testkit.TestActorRef
import com.fasterxml.jackson.core.JsonParser
import com.typesafe.config.ConfigFactory
import eu.inn.hyperbus.model.annotations.{body, request, response}
import eu.inn.hyperbus.model.{Body, Method, Request, Response}
import eu.inn.hyperbus.serialization.{MessageDeserializer, ResponseHeader}
import eu.inn.hyperbus.transport._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}


@body("mock")
case class MockBody(test: String) extends Body

@request(Method.POST, "/mock")
case class MockRequest(body: MockBody) extends Request[MockBody]

@request(Method.POST, "/not-existing")
case class MockNotExistingRequest(body: MockBody) extends Request[MockBody]

@response(200)
case class MockResponse(body: MockBody) extends Response[MockBody]

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
    val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
    transportManager = new TransportManager(transportConfiguration)
    ActorSystemRegistry.get("eu-inn").foreach { implicit actorSystem ⇒
      val testActor = TestActorRef[TestActorX]
      Cluster(actorSystem).subscribe(testActor, initialStateMode = InitialStateAsEvents, classOf[MemberEvent])
    }
  }

  after {
    if (transportManager != null) {
      Await.result(transportManager.shutdown(20.seconds), 20.seconds)
    }
  }

  "DistributedAkkaTransport " - {
    "Send and Receive" in {
      import ExecutionContext.Implicits.global
      val cnt = new AtomicInteger(0)

      val responseDeserializer = (input: InputStream) ⇒ {
        MessageDeserializer.deserializeResponseWith(input) { (responseHeader: ResponseHeader, responseBodyJson: JsonParser) ⇒
          MockResponse(MockBody(responseHeader.contentType, responseBodyJson))
        }
      }

      val idf = transportManager.onCommand(RequestMatcher(Some(Uri("/mock"))), MockRequest.apply) { case msg: MockRequest =>
        Future {
          cnt.incrementAndGet()
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }
      val id = idf.futureValue

      val id2f = transportManager.onCommand(RequestMatcher(Some(Uri("/mock"))), MockRequest.apply) { case msg: MockRequest =>
        Future {
          cnt.incrementAndGet()
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }
      val id2 = id2f.futureValue

      val id3f = transportManager.onEvent(RequestMatcher(Some(Uri("/mock"))), "sub1", MockRequest.apply) { case msg: MockRequest =>
        msg.body.test should equal("12345")
        cnt.incrementAndGet()
        Future.successful({})
      }
      val id3 = id3f.futureValue

      val id4f = transportManager.onEvent(RequestMatcher(Some(Uri("/mock"))), "sub1", MockRequest.apply) { case msg: MockRequest =>
        msg.body.test should equal("12345")
        cnt.incrementAndGet()
        Future.successful({})
      }
      val id4 = id4f.futureValue

      val id5f = transportManager.onEvent(RequestMatcher(Some(Uri("/mock"))), "sub2", MockRequest.apply) { case msg: MockRequest =>
        msg.body.test should equal("12345")
        cnt.incrementAndGet()
        Future.successful({})
      }
      val id5 = id5f.futureValue

      Thread.sleep(500) // we need to wait until subscriptions will go acros the

      val f: Future[TransportResponse] = transportManager.ask(MockRequest(MockBody("12345")), responseDeserializer)

      whenReady(f, timeout(Span(5, Seconds))) { msg =>
        msg shouldBe a[MockResponse]
        msg.asInstanceOf[MockResponse].body.test should equal("54321")
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(3)
      }

      transportManager.off(id).futureValue
      transportManager.off(id2).futureValue
      transportManager.off(id3).futureValue
      transportManager.off(id4).futureValue
      transportManager.off(id5).futureValue



      val f2: Future[TransportResponse] = transportManager.ask(MockRequest(MockBody("12345")), responseDeserializer)

      /*
      Thread.sleep(6000)
      todo: this is not working for some reason, route isn't deleted after unsubscribe?
      whenReady(f2.failed, timeout(Span(10, Seconds))) { e =>
        e shouldBe a[NoTransportRouteException]
      }
      */

      val f3: Future[TransportResponse] = transportManager.ask(MockNotExistingRequest(MockBody("12345")), responseDeserializer)

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
