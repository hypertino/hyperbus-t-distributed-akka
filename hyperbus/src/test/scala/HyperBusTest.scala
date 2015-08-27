import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.dynamic.{Null, Text, Obj}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.standard._
import eu.inn.hyperbus.serialization.{ResponseBodyDecoder, ResponseHeader}
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class ClientTransportTest(output: String) extends ClientTransport {
  private val messageBuf = new StringBuilder
  private var inputTopicVar: Topic = null

  def input = messageBuf.toString()

  def inputTopic = inputTopicVar

  override def ask[OUT, IN](topic: Topic, message: IN, inputEncoder: Encoder[IN], outputDecoder: Decoder[OUT]): Future[OUT] = {
    inputTopicVar = topic
    val ba = new ByteArrayOutputStream()
    inputEncoder(message, ba)
    messageBuf.append(ba.toString("UTF-8"))

    val os = new ByteArrayInputStream(output.getBytes("UTF-8"))
    val out = outputDecoder(os)
    Future.successful(out)
  }

  override def publish[IN](topic: Topic, message: IN, inputEncoder: Encoder[IN]): Future[Unit] = {
    ask[Any, IN](topic, message, inputEncoder, null) map { x =>
    }
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future.successful(true)
  }
}

class ServerTransportTest extends ServerTransport {
  var sInputDecoder: Decoder[Any] = null
  var sHandler: (Any) ⇒ SubscriptionHandlerResult[Any] = null
  var sExtractor: FiltersExtractor[Any] = null
  var sExceptionEncoder: Encoder[Throwable] = null

  def process[OUT, IN](topic: Topic,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: FiltersExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    sInputDecoder = inputDecoder
    sHandler = handler.asInstanceOf[(Any) ⇒ SubscriptionHandlerResult[Any]]
    sExtractor = partitionArgsExtractor.asInstanceOf[FiltersExtractor[Any]]
    sExceptionEncoder = exceptionEncoder
    ""
  }

  def off(subscriptionId: String) = ???

  //todo: test this
  def subscribe[IN](topic: Topic,
                    groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: FiltersExtractor[IN])
                   (handler: (IN) ⇒ SubscriptionHandlerResult[Unit]): String = {

    sInputDecoder = inputDecoder
    sHandler = handler.asInstanceOf[(Any) ⇒ SubscriptionHandlerResult[Any]]
    ""
  }

  def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future.successful(true)
  }
}

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    "<~ (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"contentType":"application/vnd+created-body.json","messageId":"123"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = newHyperBus(ct,null)
      val f = hyperBus <~ TestPost1(TestBody1("ha ha"), messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r.body should equal(TestCreatedBody("100500"))
      }
    }

    "<~ dynamic (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"contentType":"application/vnd+created-body.json","messageId":"123"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ DynamicPost("/resources",
        DynamicBody(
          Obj(Map("resourceData" → Text("ha ha"))),
          Some("application/vnd+test-1.json")
        ),
        messageId = "123",
        correlationId = "123"
      )

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r shouldBe a[Created[_]]
        r.body shouldBe a[DynamicCreatedBody]
      }
    }

    "<~ empty (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ TestPostWithNoContent(TestBody1("empty"), messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"url":"/empty","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"empty"}}"""
      )

      whenReady(f) { r =>
        r shouldBe a[NoContent[_]]
        r.body shouldBe a[EmptyBody]
      }
    }

    "<~ static request with dynamic body (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ StaticPostWithDynamicBody(DynamicBody(Text("ha ha")), messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"url":"/empty","method":"post","messageId":"123"},"body":"ha ha"}"""
      )

      whenReady(f) { r =>
        r shouldBe a[NoContent[_]]
        r.body shouldBe a[EmptyBody]
      }
    }

    "<~ static request with empty body (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ StaticPostWithEmptyBody(EmptyBody, messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"url":"/empty","method":"post","contentType":"no-content","messageId":"123"},"body":null}"""
      )

      whenReady(f) { r =>
        r shouldBe a[NoContent[_]]
        r.body shouldBe a[EmptyBody]
      }
    }
    
    "<~ client got exception" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":409,"messageId":"abcde12345"},"body":{"code":"failed","errorId":"abcde12345"}}"""
      )

      val hyperBus = newHyperBus(ct,null)
      val f = hyperBus <~ TestPost1(TestBody1("ha ha"), messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f.failed) { r =>
        r shouldBe a[Conflict[_]]
        r.asInstanceOf[Conflict[_]].body should equal(ErrorBody("failed", errorId = "abcde12345"))
      }
    }

    "~> (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null,st)
      hyperBus ~> { post: TestPost1 =>
        Future {
          Created(TestCreatedBody("100500"), messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPost1(TestBody1("ha ha"), messageId = "123", correlationId = "123"))

      val hres = st.sHandler(msg)
      whenReady(hres.futureResult) { r =>
        r should equal(Created(TestCreatedBody("100500"), messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":201,"contentType":"application/vnd+created-body.json","messageId":"123"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
        )
      }
    }

    "~> static request with empty body (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null,st)
      hyperBus ~> { post: StaticPostWithEmptyBody =>
        Future {
          NoContent(EmptyBody, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/empty","method":"post","contentType":"no-content","messageId":"123"},"body":null}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(StaticPostWithEmptyBody(EmptyBody, messageId = "123", correlationId = "123"))

      val hres = st.sHandler(msg)
      whenReady(hres.futureResult) { r =>
        r should equal(NoContent(EmptyBody, messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":null}"""
        )
      }
    }

    "~> static request with dynamic body (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null,st)
      hyperBus ~> { post: StaticPostWithDynamicBody =>
        Future {
          NoContent(EmptyBody, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/empty","method":"post","contentType":"some-content","messageId":"123"},"body":"haha"}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(StaticPostWithDynamicBody(DynamicBody(Text("haha"), Some("some-content")), messageId = "123", correlationId = "123"))

      val hres = st.sHandler(msg)
      whenReady(hres.futureResult) { r =>
        r should equal(NoContent(EmptyBody, messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":null}"""
        )
      }
    }

    "~> (server throw exception)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null,st)
      hyperBus ~> { post: TestPost1 =>
        Future {
          throw Conflict(ErrorBody("failed", errorId = "abcde12345"), messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDecoder(ba)
      msg should equal(TestPost1(TestBody1("ha ha"), messageId = "123", correlationId = "123"))

      val hres = st.sHandler(msg)
      whenReady(hres.futureResult) { r =>
        r shouldBe a[Conflict[_]]
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":409,"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
        )
      }
    }
  }

  def newHyperBus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, AnyValue))
    val sr = List(TransportRoute(st, AnyValue))
    val serviceBus = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(serviceBus)
  }
}
