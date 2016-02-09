import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.atomic.AtomicLong

import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.standard._
import eu.inn.hyperbus.serialization.RequestHeader
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri.{Uri,AnyValue}
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
  var sUriFilter: Uri = null
  var sInputDeserializer: Deserializer[TransportRequest] = null
  var sHandler: (TransportRequest) ⇒ Future[TransportResponse] = null
  var sSubscriptionHandler: (TransportRequest) ⇒ Future[Unit] = null
  var sExceptionSerializer: Serializer[Throwable] = null
  var sSubscriptionId: String = null
  val idCounter = new AtomicLong(0)

  override def process[IN <: TransportRequest](uriFilter: Uri, inputDeserializer: Deserializer[IN], exceptionSerializer: Serializer[Throwable])
                                              (handler: (IN) => Future[TransportResponse]): String = {

    sInputDeserializer = inputDeserializer
    sHandler = handler.asInstanceOf[(TransportRequest) ⇒ Future[TransportResponse]]
    sExceptionSerializer = exceptionSerializer
    idCounter.incrementAndGet().toHexString
  }

  override def subscribe[IN <: TransportRequest](uriFilter: Uri, groupName: String, inputDeserializer: Deserializer[IN])
                                                (handler: (IN) => Future[Unit]): String = {
    sInputDeserializer = inputDeserializer
    sSubscriptionHandler = handler.asInstanceOf[(TransportRequest) ⇒ Future[Unit]]
    idCounter.incrementAndGet().toHexString;
  }

  override def off(subscriptionId: String) = {
    sSubscriptionId = subscriptionId
    sInputDeserializer = null
    sSubscriptionHandler = null
    sHandler = null
    sExceptionSerializer = null
  }

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
      val f = hyperBus <~ TestPost1(TestBody1("ha ha"), headers = Map.empty, messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/resources"},"method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
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
      val f = hyperBus <~ DynamicPost(Uri("/resources"),
        DynamicBody(
          Some("application/vnd+test-1.json"),
          Obj(Map("resourceData" → Text("ha ha")))
        ),
        headers = Map.empty,
        messageId = "123",
        correlationId = "123"
      )

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/resources"},"method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
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
      val f = hyperBus <~ TestPostWithNoContent(TestBody1("empty"), headers = Map.empty, messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/empty"},"method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"empty"}}"""
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
      val f = hyperBus <~ StaticPostWithDynamicBody(DynamicBody(Text("ha ha")), headers = Map.empty, messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/empty"},"method":"post","messageId":"123"},"body":"ha ha"}"""
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
      val f = hyperBus <~ StaticPostWithEmptyBody(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/empty"},"method":"post","contentType":"no-content","messageId":"123"},"body":null}"""
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
      val f = hyperBus <~ TestPost1(TestBody1("ha ha"), headers = Map.empty, messageId = "123", correlationId = "123")

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/resources"},"method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
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
          Created(TestCreatedBody("100500"), headers = Map.empty, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"uri":{"pattern":"/resources"},"method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(TestPost1(TestBody1("ha ha"), headers = Map.empty, messageId = "123", correlationId = "123"))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(Created(TestCreatedBody("100500"), headers = Map.empty, messageId = "123", correlationId = "123"))
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
          NoContent(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"uri":{"pattern":"/empty"},"method":"post","contentType":"no-content","messageId":"123"},"body":null}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(StaticPostWithEmptyBody(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123"))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(NoContent(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123"))
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
          NoContent(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"uri":{"pattern":"/empty"},"method":"post","contentType":"some-content","messageId":"123"},"body":"haha"}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(StaticPostWithDynamicBody(DynamicBody(Some("some-content"), Text("haha")), headers = Map.empty, messageId = "123", correlationId = "123"))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(NoContent(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        r.serialize(ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":null}"""
        )
      }
    }

    "~> dynamic request (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus.onCommand(Uri("/test"), Method.GET, None) { request =>
        Future {
          NoContent(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"uri":{"pattern":"/test"},"method":"get","contentType":"some-content","messageId":"123"},"body":"haha"}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(DynamicRequest(RequestHeader(Uri("/test"), Method.GET, Some("some-content"), "123", Some("123"), Map.empty),
        DynamicBody(Some("some-content"), Text("haha")))
      )

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(NoContent(EmptyBody, headers = Map.empty, messageId = "123", correlationId = "123"))
        val ba = new ByteArrayOutputStream()
        r.serialize(ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":204,"contentType":"no-content","messageId":"123"},"body":null}"""
        )
      }
    }

    "<| static request publishing (client)" in {
      val rsp = """{"response":{"status":409,"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
      var sentEvents = List[TransportRequest]()
      val clientTransport = new ClientTransportTest(rsp) {
        override def publish(message: TransportRequest): Future[PublishResult] = {
          Future {
            sentEvents = sentEvents :+ message
            new PublishResult {
              def sent = None
              def offset = None
            }
          }
        }
      }

      val hyperBus = newHyperBus(clientTransport, null)
      val futureResult = hyperBus <| TestPost1(TestBody1("ha ha"), headers = Map.empty, messageId = "123", correlationId = "123")
      whenReady(futureResult) { r =>
        sentEvents.size should equal(1)
      }
    }

    "<| dynamic request publishing (client)" in {
      val rsp = """{"response":{"status":409,"messageId":"123"},"body":{"code":"failed","errorId":"abcde12345"}}"""
      var sentEvents = List[TransportRequest]()
      val clientTransport = new ClientTransportTest(rsp) {
        override def publish(message: TransportRequest): Future[PublishResult] = {
          Future {
            sentEvents = sentEvents :+ message
            new PublishResult {
              def sent = None
              def offset = None
            }
          }
        }
      }

      val hyperBus = newHyperBus(clientTransport, null)
      val futureResult = hyperBus <| DynamicPost(Uri("/resources"),
        DynamicBody(Some("application/vnd+test-1.json"), Obj(Map("resourceData" → Text("ha ha")))),
        headers = Map.empty,
        messageId = "123",
        correlationId = "123"
      )
      whenReady(futureResult) { r =>
        sentEvents.size should equal(1)
      }
    }

    "|> static request subscription (server)" in {
      var receivedEvents = 0
      val serverTransport = new ServerTransportTest() {
        override def subscribe[IN <: TransportRequest](uriFilter: Uri, groupName: String, inputDeserializer: Deserializer[IN])(handler: (IN) => Future[Unit]): String =  {
          receivedEvents += 1
          super.subscribe(uriFilter, groupName, inputDeserializer)(handler)
        }
      }
      val hyperBus = newHyperBus(null, serverTransport)

      hyperBus |> { request: TestPost2 =>
        Future {}
      }

      receivedEvents should equal(1)
    }

    "|> dynamic request subscription (server)" in {
      var receivedEvents = 0
      val serverTransport = new ServerTransportTest() {
        override def subscribe[IN <: TransportRequest](uriFilter: Uri, groupName: String, inputDeserializer: Deserializer[IN])(handler: (IN) => Future[Unit]): String =  {
          receivedEvents += 1
          super.subscribe(uriFilter, groupName, inputDeserializer)(handler)
        }
      }
      val hyperBus = newHyperBus(null, serverTransport)

      hyperBus.onEvent(Uri("/test"), Method.GET, None, Some("group1")) { request: DynamicRequest => Future {} }
      receivedEvents should equal(1)
    }

    "~> (server throw exception)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: TestPost1 =>
        Future {
          throw Conflict(ErrorBody("failed", errorId = "abcde12345"), headers = Map.empty, messageId = "123", correlationId = "123")
        }
      }

      val req = """{"request":{"uri":{"pattern":"/resources"},"method":"post","contentType":"application/vnd+test-1.json","messageId":"123"},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = st.sInputDeserializer(ba)
      msg should equal(TestPost1(TestBody1("ha ha"), headers = Map.empty, messageId = "123", correlationId = "123"))

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

    "off test ~> (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      val id1 = hyperBus ~> { post: TestPostWithNoContent =>
        Future {
          NoContent(EmptyBody)
        }
      }

      st.sHandler shouldNot equal(null)
      hyperBus.off(id1)
      st.sHandler should equal(null)
      st.sSubscriptionId should equal(id1)

      val id2 = hyperBus ~> { post: TestPostWithNoContent =>
        Future {
          NoContent(EmptyBody)
        }
      }

      st.sHandler shouldNot equal(null)
      hyperBus.off(id2)
      st.sHandler should equal(null)
      st.sSubscriptionId should equal(id2)
    }
  }

  "off test |> (server)" in {
    val st = new ServerTransportTest()
    val hyperBus = newHyperBus(null, st)
    val id1 = hyperBus |> { request: TestPost1 => Future {} }

    st.sSubscriptionHandler shouldNot equal(null)
    hyperBus.off(id1)
    st.sSubscriptionHandler should equal(null)
    st.sSubscriptionId should equal(id1)

    val id2 = hyperBus |> { request: TestPost1 => Future {} }

    st.sSubscriptionHandler shouldNot equal(null)
    hyperBus.off(id2)
    st.sSubscriptionHandler should equal(null)
    st.sSubscriptionId should equal(id2)
  }

  def newHyperBus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, Uri(AnyValue)))
    val sr = List(TransportRoute(st, Uri(AnyValue)))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(transportManager, Some("group1"))
  }
}
