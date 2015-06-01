import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import eu.inn.servicebus.transport.distributedakka.{DistributedAkkaServerTransport, DistributedAkkaClientTransport}
import eu.inn.servicebus.{TransportRoute, ServiceBus}
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DistribAkkaTransportTest extends FreeSpec with ScalaFutures with Matchers {
  "DistributedAkkaTransport " - {
    "Send and Receive" in {
      val clientTransport = new DistributedAkkaClientTransport()
      val serverTransport = new DistributedAkkaServerTransport()

      val cr = List(TransportRoute[ClientTransport](clientTransport, AnyArg))
      val sr = List(TransportRoute[ServerTransport](serverTransport, AnyArg))
      val serviceBus = new ServiceBus(cr, sr)
      val cnt = new AtomicInteger(0)

      val id = serviceBus.on[String, Int](Topic("/topic/{abc}", PartitionArgs(Map())), mockExtractor[Int]) { s =>
        Future {
          cnt.incrementAndGet()
          s.toString.reverse
        }
      }

      val id2 = serviceBus.on[String, Int](Topic("/topic/{abc}", PartitionArgs(Map())), mockExtractor[Int]) { s =>
        Future {
          cnt.incrementAndGet()
          s.toString.reverse
        }
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map())), "sub1", mockExtractor[String]) { s =>
        s should equal("12345")
        cnt.incrementAndGet()
        Future.successful {}
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map())), "sub1", mockExtractor[String]) { s =>
        s should equal("12345")
        cnt.incrementAndGet()
        Future.successful {}
      }

      serviceBus.subscribe[String](Topic("/topic/{abc}", PartitionArgs(Map())), "sub2", mockExtractor[String]) { s =>
        s should equal("12345")
        cnt.incrementAndGet()
        Future.successful {}
      }

      val f: Future[String] = serviceBus.ask[String, Int](Topic("/topic/{abc}", PartitionArgs(Map())), 12345)

      whenReady(f) { s =>
        s should equal("54321")
        Thread.sleep(500) // give chance to increment to another service (in case of wrong implementation)
        cnt.get should equal(3)
        /*
        serviceBus.off(id)

        todo: NoTransportRouteException doesn't work for DistribPubSub
        val f2: Future[String] = serviceBus.ask[String, Int](Topic("topic", PartitionArgs(Map())), 1)
        whenReady(f2.failed, timeout(Span(10, Seconds))) { e =>
          e shouldBe a[NoTransportRouteException]
        }*/
      }
    }
  }

  def mockExtractor[T]: PartitionArgsExtractor[T] = {
    (x: T) => PartitionArgs(Map())
  }
}
