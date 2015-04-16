import eu.inn.servicebus.transport.InprocTransport
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

      serviceBus.subscribe("topic", None, { s: String =>
        Future {
          s.reverse
        }
      })

      val f: Future[String] = serviceBus.send[String,String]("topic", "hey")

      whenReady(f) { s =>
        s should equal ("yeh")
      }
    }
  }
}
