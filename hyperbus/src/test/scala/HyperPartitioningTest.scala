import java.io.ByteArrayInputStream

import eu.inn.binders.dynamic.Obj
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.impl.Helpers
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.annotations.{body, request}
import eu.inn.hyperbus.rest.standard.{Ok, StaticPost}
import eu.inn.hyperbus.utils.IdUtils
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.{ExecutionContext, Future}

@body("application/vnd+parition.json")
case class TestPartition(partitionId: String, data: String) extends Body

@request("/resources/{partitionId}")
case class TestPostPartition1(body: TestPartition) extends StaticPost(body)
with DefinedResponse[Ok[DynamicBody]]


class HyperPartitioningTest extends FreeSpec with Matchers with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  "HyperPartitioning " - {
    "Partitioning when asking" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":200,"messageId":"123"},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ TestPostPartition1(TestPartition("1", "ha"), messageId = "123", correlationId = "123")

      ct.inputTopic should equal(
        Topic("/resources/{partitionId}", Map("partitionId" → "1"))
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

      val partitionArgs = st.sExtractor(msg)
      partitionArgs should equal(
        Map("partitionId" → "123")
      )
    }

    "Parse Url" in {
      val p: String ⇒ Seq[String] = Helpers.extractParametersFromUrl
      p("{abc}") should equal(Seq("abc"))
      p("/{abc}/") should equal(Seq("abc"))
      p("x/{abc}/y") should equal(Seq("abc"))
      p("x/{abc}/y/{def}") should equal(Seq("abc", "def"))
      p("{abc}{def}") should equal(Seq("abc", "def"))
    }
  }

  // todo: add partition tests for Dynamic

  def newHyperBus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, AllowAny))
    val sr = List(TransportRoute(st, AllowAny))
    val serviceBus = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(serviceBus)
  }
}
