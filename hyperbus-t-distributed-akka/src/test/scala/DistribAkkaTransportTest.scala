import java.io.{InputStream, OutputStream}
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive
import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import eu.inn.binders._
import eu.inn.binders.json._
import eu.inn.hyperbus.IdGenerator
import eu.inn.hyperbus.transport._
import eu.inn.hyperbus.transport.api._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await, Future, Promise}


// move mocks to separate assembly
case class MockRequest(specificTopic: String, message: String,
                       correlationId: String = IdGenerator.create(),
                       messageId: String = IdGenerator.create()) extends TransportRequest {
  def topic: Topic = Topic(specificTopic)
  override def encode(output: OutputStream): Unit = {
    SerializerFactory.findFactory().withStreamGenerator(output)(_.bind(this))
  }
}

case class MockResponse(message: String,
                        correlationId: String = IdGenerator.create(),
                        messageId: String = IdGenerator.create()) extends TransportResponse {
  override def encode(output: OutputStream): Unit = {
    SerializerFactory.findFactory().withStreamGenerator(output)(_.bind(this))
  }
}

object MockRequestDecoder extends Decoder[MockRequest] {
  override def apply(input: InputStream): MockRequest = {
    SerializerFactory.findFactory().withStreamParser(input)(_.unbind[MockRequest])
  }
}

object MockResponseDecoder extends Decoder[MockResponse] {
  override def apply(input: InputStream): MockResponse = {
    SerializerFactory.findFactory().withStreamParser(input)(_.unbind[MockResponse])
  }
}

class TestActorX extends Actor with ActorLogging{
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
      Await.result(transportManager.shutdown(10.seconds), 10.seconds)
    }
  }

  "DistributedAkkaTransport " - {
    "Send and Receive" in {
      import ExecutionContext.Implicits.global
      val cnt = new AtomicInteger(0)

      val id = transportManager.process(Topic("/topic/{abc}"), MockRequestDecoder, null) { msg: MockRequest =>
        Future {
          cnt.incrementAndGet()
          MockResponse(msg.message.reverse)
        }
      }

      val id2 = transportManager.process(Topic("/topic/{abc}"), MockRequestDecoder, null){ msg: MockRequest =>
        Future {
          cnt.incrementAndGet()
          MockResponse(msg.message.reverse)
        }
      }

      transportManager.subscribe(Topic("/topic/{abc}"), "sub1", MockRequestDecoder) { msg: MockRequest =>
        msg.message should equal("12345")
        cnt.incrementAndGet()
        Future.successful({})
      }

      transportManager.subscribe(Topic("/topic/{abc}"), "sub1", MockRequestDecoder) { msg: MockRequest =>
        msg.message should equal("12345")
        cnt.incrementAndGet()
        Future.successful({})
      }

      transportManager.subscribe(Topic("/topic/{abc}"), "sub2", MockRequestDecoder) { msg: MockRequest =>
        msg.message should equal("12345")
        cnt.incrementAndGet()
        Future.successful({})
      }

      Thread.sleep(500) // we need to wait until subscriptions will go acros the

      val f: Future[MockResponse] = transportManager.ask(MockRequest("/topic/{abc}", "12345"), MockResponseDecoder)

      whenReady(f) { msg =>
        msg shouldBe a[MockResponse]
        msg.message should equal("54321")
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(3)

        transportManager.off(id)
        transportManager.off(id2)

        /*
        Thread.sleep(500) // todo: find a way to know if the subscription is complete? future?
        // todo: NoTransportRouteException doesn't work for DistribPubSub

        val f2: Future[String] = serviceBus.ask[String, String](Topic("/topic/{abc}", PartitionArgs(Map.empty)), "12345",
          mockEncoder, mockDecoder)
        whenReady(f2.failed, timeout(Span(1, Seconds))) { e =>
          e shouldBe a[NoTransportRouteException]
        }

        val f3: Future[String] = serviceBus.ask[String, String](Topic("not-existing-topic", PartitionArgs(Map.empty)), "12345",
          mockEncoder, mockDecoder)
        whenReady(f3.failed, timeout(Span(1, Seconds))) { e =>
          e shouldBe a[NoTransportRouteException]
        }*/
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
