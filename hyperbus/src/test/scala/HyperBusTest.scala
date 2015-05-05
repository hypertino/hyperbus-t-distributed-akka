import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.annotations.fieldName
import eu.inn.binders.dynamic.Text
import eu.inn.hyperbus.protocol._
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.protocol.annotations.{url, contentType}
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.transport.{SubscriptionHandlerResult, ServerTransport, ClientTransport, InprocTransport}
import eu.inn.servicebus.ServiceBus
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, FreeSpec}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future}

class ClientTransportTest(output: String) extends ClientTransport {
  private val buf = new StringBuilder
  def input = buf.toString()
  
  override def ask[OUT, IN](topic: String, message: IN, inputEncoder: Encoder[IN], outputDecoder: Decoder[OUT]): Future[OUT] = {
    val ba = new ByteArrayOutputStream()
    inputEncoder(message, ba)
    buf.append(ba.toString("UTF-8"))

    val os = new ByteArrayInputStream(output.getBytes("UTF-8"))
    val out = outputDecoder(os)
    val p: Promise[OUT] = Promise()
    p.success(out)
    p.future
  }
}

class ServerTransportTest extends ServerTransport {
  var sInputDecoder: Decoder[Any] = null
  var sHandler: (Any) ⇒ SubscriptionHandlerResult[Any] = null

  def on[OUT, IN](topic: String, groupName: Option[String], inputDecoder: Decoder[IN])
                        (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    sInputDecoder = inputDecoder
    sHandler = handler.asInstanceOf[(Any) ⇒ SubscriptionHandlerResult[Any]]
    ""
  }

  def off(subscriptionId: String) = ???
}

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    "Send (serialize)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"contentType":"application/vnd+created-body.json"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = new HyperBus(new ServiceBus(ct,null))
      val f = hyperBus ? TestPost1(TestBody1("ha ha"))

      ct.input should equal (
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r.body should equal(TestCreatedBody("100500"))
      }
    }

    "Subscribe (serialize)" in {
      val st = new ServerTransportTest()
      val hyperBus = new HyperBus(new ServiceBus(null,st))
      hyperBus.on[TestPost1](None) { post =>
        Future {
          Created(TestCreatedBody("100500"))
        }
      }

      val req = """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPost1(TestBody1("ha ha")))

      val hres = st.sHandler(msg)
      whenReady(hres.futureResult) { r =>
        r should equal(Created(TestCreatedBody("100500")))
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":201,"contentType":"application/vnd+created-body.json"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
        )
      }
    }

    "Subscribe (serialize exception)" in {
      val st = new ServerTransportTest()
      val hyperBus = new HyperBus(new ServiceBus(null,st))
      hyperBus.on[TestPost1](None) { post =>
        Future {
          throw new ConflictError(Error("failed", errorId = "abcde12345"))
        }
      }

      val req = """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPost1(TestBody1("ha ha")))

      val hres = st.sHandler(msg)
      whenReady(hres.futureResult) { r =>
        r should equal(Created(TestCreatedBody("100500")))
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":409},"body":{"code":"failed"}}"""
        )
      }
    }
  }
}
