import java.io.{InputStream, OutputStream}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorLogging, Actor, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.event.LoggingReceive
import akka.testkit.TestActorRef
import com.typesafe.config.ConfigFactory
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import eu.inn.servicebus.{ServiceBus, ServiceBusConfigurationLoader}
import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

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

  var serviceBus: ServiceBus = null
  before {
    val serviceBusConfig = ServiceBusConfigurationLoader.fromConfig(ConfigFactory.load())
    serviceBus = new ServiceBus(serviceBusConfig)
    ActorSystemRegistry.get("eu-inn").foreach { implicit actorSystem ⇒
      val testActor = TestActorRef[TestActorX]
      Cluster(actorSystem).subscribe(testActor, initialStateMode = InitialStateAsEvents, classOf[MemberEvent])
    }
  }

  after {
    if (serviceBus != null) {
      Await.result(serviceBus.shutdown(10.seconds), 10.seconds)
    }
  }

  "DistributedAkkaTransport " - {
    "Send and Receive" in {
      val cnt = new AtomicInteger(0)

      val id = serviceBus.process[String, String](Topic("/topic/{abc}", PartitionArgs(Map.empty)),
        mockDecoder, mockExtractor[String], null) { s =>
        cnt.incrementAndGet()
        mockResult(s.reverse)
      }

      val id2 = serviceBus.process[String, String](Topic("/topic/{abc}", PartitionArgs(Map.empty)),
        mockDecoder,
        mockExtractor[String], null){ s =>
        cnt.incrementAndGet()
        mockResult(s.reverse)
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map.empty)), "sub1",
        mockDecoder,
        mockExtractor[String]) { s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map.empty)), "sub1",
        mockDecoder,
        mockExtractor[String]){ s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map.empty)), "sub2",
        mockDecoder,
        mockExtractor[String]){ s =>
        s should equal("12345")
        cnt.incrementAndGet()
        mockResultU
      }

      Thread.sleep(500) // we need to wait until subscriptions will go acros the

      val f: Future[String] = serviceBus.ask[String, String](Topic("/topic/{abc}", PartitionArgs(Map.empty)),
        "12345",
        mockEncoder, mockDecoder)

      whenReady(f) { s =>
        s should equal("54321")
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(3)

        serviceBus.off(id)
        serviceBus.off(id2)

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
  }

  def mockExtractor[T]: PartitionArgsExtractor[T] = {
    (x: T) => PartitionArgs(Map.empty)
  }

  def mockEncoder(in: String, out: OutputStream) = {
    out.write(in.getBytes("UTF-8"))
  }

  def mockDecoder(in: InputStream): String = {
    IOUtils.toString(in, "UTF-8")
  }

  def mockResult(result: String): SubscriptionHandlerResult[String] = {
    SubscriptionHandlerResult[String](Future.successful(result), mockEncoder)
  }

  def mockResultU: SubscriptionHandlerResult[Unit] = {
    SubscriptionHandlerResult[Unit](Future.successful{}, null)
  }
}
