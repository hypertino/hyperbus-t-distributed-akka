import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.transport.api._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class ClientTransportTest(output: String) extends ClientTransport {
  private val messageBuf = new StringBuilder

  def input = messageBuf.toString()

  override def ask[OUT <: TransportResponse](message: TransportRequest, outputDeserializer: Deserializer[OUT]): Future[OUT] = {
    val ba = new ByteArrayOutputStream()
    message.serialize(ba)
    messageBuf.append(ba.toString("UTF-8"))

    val os = new ByteArrayInputStream(output.getBytes("UTF-8"))
    val out = outputDeserializer(os)
    Future.successful(out)
  }

  override def publish(message: TransportRequest): Future[PublishResult] = {
    ask(message, null) map { x =>
      new PublishResult {
        def sent = None

        def offset = None
      }
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future.successful(true)
  }
}

class ServerTransportTest extends ServerTransport {
  var sTopicFilter: Topic = null
  var sInputDeserializer: Deserializer[TransportRequest] = null
  var sHandler: (TransportRequest) ⇒ Future[TransportResponse] = null
  var sExceptionSerializer: Serializer[Throwable] = null

  override def process[IN <: TransportRequest](topicFilter: Topic, inputDeserializer: Deserializer[IN], exceptionSerializer: Serializer[Throwable])
                                              (handler: (IN) => Future[TransportResponse]): String = {

    sInputDeserializer = inputDeserializer
    sHandler = handler.asInstanceOf[(TransportRequest) ⇒ Future[TransportResponse]]
    sExceptionSerializer = exceptionSerializer
    ""
  }

  //todo: test this
  override def subscribe[IN <: TransportRequest](topicFilter: Topic, groupName: String, inputDeserializer: Deserializer[IN])
                                                (handler: (IN) => Future[Unit]): String = ???

  override def off(subscriptionId: String) = ???

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future.successful(true)
  }
}

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    "<~ (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"contentType":"application/vnd+created-body.json","messageId":"123"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = newHyperBus(ct, null)
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
          Some("application/vnd+test-1.json"),
          Obj(Map("resourceData" → Text("ha ha")))
        ),
        messageId = "123",
        correlationId = "123"
      )

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r shouldBe a[Created[_]]
        r.body shouldBe a[DynamicBody]
        r.body shouldBe a[CreatedBody]
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

      val hyperBus = newHyperBus(ct, null)
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
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: TestPost1 =>
        Future {
          Created(TestCreatedBody("100500"), messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(TestPost1(TestBody1("ha ha"), messageId = "123", correlationId = "123"))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(Created(TestCreatedBody("100500"), messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        r.serialize(ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":201,"contentType":"application/vnd+created-body.json","messageId":"123"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
        )
      }
    }

    "~> static request with empty body (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: StaticPostWithEmptyBody =>
        Future {
          NoContent(EmptyBody, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/empty","method":"post","contentType":"no-content","messageId":"123"},"body":null}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(StaticPostWithEmptyBody(EmptyBody, messageId = "123", correlationId = "123"))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(NoContent(EmptyBody, messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        r.serialize(ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":null}"""
        )
      }
    }

    "~> static request with dynamic body (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: StaticPostWithDynamicBody =>
        Future {
          NoContent(EmptyBody, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/empty","method":"post","contentType":"some-content","messageId":"123"},"body":"haha"}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(StaticPostWithDynamicBody(DynamicBody(Some("some-content"), Text("haha")), messageId = "123", correlationId = "123"))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(NoContent(EmptyBody, messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        r.serialize(ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":null}"""
        )
      }
    }

    "~> (server throw exception)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: TestPost1 =>
        Future {
          throw Conflict(ErrorBody("failed", errorId = "abcde12345"), messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(TestPost1(TestBody1("ha ha"), messageId = "123", correlationId = "123"))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r shouldBe a[Conflict[_]]
        val ba = new ByteArrayOutputStream()
        r.serialize(ba)
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
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(transportManager)
  }
}
