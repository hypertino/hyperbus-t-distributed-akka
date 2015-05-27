import java.io.OutputStream

import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport.{PartitionArgs, Topic, NoTransportRouteException, InprocTransport}
import eu.inn.servicebus.ServiceBus
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, FreeSpec}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class ServiceBusTest extends FreeSpec with ScalaFutures with Matchers {
  "ServiceBus " - {
    "Send and Receive" in {
      val tr = new InprocTransport
      val serviceBus = new ServiceBus(tr,tr)

      val id = serviceBus.on[String,Int](Topic("topic", PartitionArgs(Map())), mockExtractor[Int]) { s =>
        Future {
          s.toString.reverse
        }
      }

      val f: Future[String] = serviceBus.ask[String,Int](Topic("topic", PartitionArgs(Map())), 12345)

      whenReady(f) { s =>
        s should equal ("54321")
        serviceBus.off(id)

        val f2: Future[String] = serviceBus.ask[String,Int](Topic("topic", PartitionArgs(Map())), 1)
        whenReady(f2.failed) { e =>
          e shouldBe a [NoTransportRouteException]
        }
      }
    }
  }
  def mockExtractor[T]: PartitionArgsExtractor[T] = {
    (x:T) => PartitionArgs(Map())
  }
}
