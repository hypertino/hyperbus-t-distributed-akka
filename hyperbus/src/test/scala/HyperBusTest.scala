import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.protocol._
import eu.inn.servicebus.ServiceBus
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientTransportTest(output: String) extends ClientTransport {
  private val buf = new StringBuilder
  def input = buf.toString()
  
  override def ask[OUT, IN](topic: String, message: IN, inputEncoder: Encoder[IN], outputDecoder: Decoder[OUT]): Future[OUT] = {
    val ba = new ByteArrayOutputStream()
    inputEncoder(message, ba)
    buf.append(ba.toString("UTF-8"))

    val os = new ByteArrayInputStream(output.getBytes("UTF-8"))
    val out = outputDecoder(os)
    Future.successful(out)
  }

  override def publish[IN](topic: String, message: IN, inputEncoder: Encoder[IN]): Future[PublishResult] = {
    ask[Any,IN](topic, message, inputEncoder, null) map { x =>
      new PublishResult {
        override def messageId: String = "1"
      }
    }
  }
}

class ServerTransportTest extends ServerTransport {
  var sInputDecoder: Decoder[Any] = null
  var sHandler: (Any) ⇒ SubscriptionHandlerResult[Any] = null

  def on[OUT, IN](topic: String, groupName: Option[String], position: SeekPosition, inputDecoder: Decoder[IN])
                        (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    sInputDecoder = inputDecoder
    sHandler = handler.asInstanceOf[(Any) ⇒ SubscriptionHandlerResult[Any]]
    ""
  }

  def off(subscriptionId: String) = ???

  override def seek(subscriptionId: String, position: SeekPosition): Unit = ???
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

    "Send (serialize exception)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":409},"body":{"code":"failed","errorId":"abcde12345"}}"""
      )

      val hyperBus = new HyperBus(new ServiceBus(ct,null))
      val f = hyperBus ? TestPost1(TestBody1("ha ha"))

      ct.input should equal (
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f.failed) { r =>
        r should equal (Conflict(ErrorBody("failed",errorId="abcde12345")))
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
          throw new Conflict(ErrorBody("failed", errorId = "abcde12345"))
        }
      }

      val req = """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPost1(TestBody1("ha ha")))

      val hres = st.sHandler(msg)
      whenReady(hres.futureResult) { r =>
        r shouldBe a [Conflict[_]]
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":409},"body":{"code":"failed","errorId":"abcde12345"}}"""
        )
      }
    }
  }
}
