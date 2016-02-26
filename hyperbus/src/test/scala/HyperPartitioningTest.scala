import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.transport.api.matchers.{AnyValue, TransportRequestMatcher}
import eu.inn.hyperbus.transport.api.uri.Uri
import eu.inn.hyperbus.transport.api.{ClientTransport, ServerTransport, TransportManager, TransportRoute}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext
/*

@body("application/vnd+parition.json")
case class TestPartition(data: String) extends Body

@request(Method.POST, "/resources/{partitionId}")
case class TestPostPartition1(partitionId: String, body: TestPartition) extends Request[TestPartition]
  with DefinedResponse[Ok[DynamicBody]]


class HyperPartitioningTest extends FreeSpec with Matchers with ScalaFutures {

  //todo: real partition test (with different suscribers)
  "HyperPartitioning " - {
    "Partitioning when asking" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":200,"messageId":"123"},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ TestPostPartition1(TestPartition("1", "ha"), messageId = "123", correlationId = "123")

      ct.inputTopic should equal(
        Topic("/resources/{partitionId}", Filters(Map("partitionId" → SpecificValue("1"))))
      )

      whenReady(f) { r =>
        r.body should equal(DynamicBody(Obj()))
      }
    }

    "Partitioning when serving" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: TestPostPartition1 =>
        Future {
          Ok(DynamicBody(Obj()))
        }
      }

      val req = """{"request":{"url":"/resources/{partitionId}","method":"post","contentType":"application/vnd+parition.json","messageId":"123"},"body":{"partitionId":"123","data":"abc"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPostPartition1(TestPartition("123", "abc"), messageId = "123", correlationId = "123"))

      msg.topic.valueFilters should equal(
        Filters(Map("partitionId" → SpecificValue("123")))
      )
    }
  }

  // todo: add partition tests for Dynamic

  def newHyperBus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, TransportRequestMatcher(Some(Uri(AnyValue)))))
    val sr = List(TransportRoute(st, TransportRequestMatcher(Some(Uri(AnyValue)))))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(transportManager, logMessages = true)
  }
}

*/