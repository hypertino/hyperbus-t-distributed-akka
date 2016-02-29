import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.concurrent.atomic.AtomicLong

import eu.inn.binders.dynamic.{Obj, Text}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.model._
import eu.inn.hyperbus.serialization._
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.{Any, Specific, RequestMatcher}
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class ClientTransportTest(output: String) extends ClientTransport {
  private val messageBuf = new StringBuilder

  def input = messageBuf.toString()

  override def ask(message: TransportRequest, outputDeserializer: Deserializer[TransportResponse]): Future[TransportResponse] = {
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

case class ServerSubscriptionTest(id: String) extends Subscription

class ServerTransportTest extends ServerTransport {
  var sUriFilter: Uri = null
  var sInputDeserializer: RequestDeserializer[Request[Body]] = null
  var sHandler: (TransportRequest) ⇒ Future[TransportResponse] = null
  var sSubscriptionHandler: (TransportRequest) ⇒ Future[Unit] = null
  var sSubscriptionId: String = null
  val idCounter = new AtomicLong(0)

  override def onCommand(matcher: RequestMatcher,
                         inputDeserializer: RequestDeserializer[Request[Body]])
                        (handler: (Request[Body]) => Future[TransportResponse]): Future[Subscription] = {

    sInputDeserializer = inputDeserializer
    sHandler = handler.asInstanceOf[(TransportRequest) ⇒ Future[TransportResponse]]
    Future {
      ServerSubscriptionTest(idCounter.incrementAndGet().toHexString)
    }
  }

  override def onEvent(matcher: RequestMatcher,
                       groupName: String,
                       inputDeserializer: RequestDeserializer[Request[Body]])
                      (handler: (Request[Body]) => Future[Unit]): Future[Subscription] = {
    sInputDeserializer = inputDeserializer
    sSubscriptionHandler = handler.asInstanceOf[(TransportRequest) ⇒ Future[Unit]]
    Future {
      ServerSubscriptionTest(idCounter.incrementAndGet().toHexString)
    }
  }

  override def off(subscription: Subscription): Future[Unit] = Future {
    subscription match {
      case ServerSubscriptionTest(subscriptionId) ⇒
        sSubscriptionId = subscriptionId
        sInputDeserializer = null
        sSubscriptionHandler = null
        sHandler = null
    }
  }

  override def shutdown(duration: FiniteDuration): Future[Boolean] = {
    Future.successful(true)
  }
}

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {
    implicit val mcx = new MessagingContextFactory {
      override def newContext(): MessagingContext = new MessagingContext {
        override def correlationId: String = "123"

        override def messageId: String = "123"
      }
    }

    "<~ (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"headers":{"contentType":["application/vnd+created-body.json"],"messageId":["123"]}},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ TestPost1(TestBody1("ha ha"))

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/resources"},"headers":{"messageId":["123"],"method":["post"],"contentType":["application/vnd+test-1.json"]}},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r.body should equal(TestCreatedBody("100500"))
      }
    }

    "<~ dynamic (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"headers":{"contentType":["application/vnd+created-body.json"],"messageId":["123"]}},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ DynamicRequest(Uri("/resources"),
        Method.POST,
        DynamicBody(
          Some("application/vnd+test-1.json"),
          Obj(Map("resourceData" → Text("ha ha")))
        )
      )

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/resources"},"headers":{"messageId":["123"],"method":["post"],"contentType":["application/vnd+test-1.json"]}},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r shouldBe a[Created[_]]
        r.body shouldBe a[DynamicBody]
        r.body shouldBe a[CreatedBody]
      }
    }

    "<~ empty (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":204,"headers":{"messageId":["123"]}},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ TestPostWithNoContent(TestBody1("empty"))

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/empty"},"headers":{"messageId":["123"],"method":["post"],"contentType":["application/vnd+test-1.json"]}},"body":{"resourceData":"empty"}}"""
      )

      whenReady(f) { r =>
        r shouldBe a[NoContent[_]]
        r.body shouldBe a[EmptyBody]
      }
    }

    "<~ static request with dynamic body (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":204,"headers":{"messageId":["123"]}},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ StaticPostWithDynamicBody(DynamicBody(Text("ha ha")))

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/empty"},"headers":{"messageId":["123"],"method":["post"]}},"body":"ha ha"}"""
      )

      whenReady(f) { r =>
        r shouldBe a[NoContent[_]]
        r.body shouldBe a[EmptyBody]
      }
    }

    "<~ static request with empty body (client)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":204,"headers":{"messageId":["123"]}},"body":{}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ StaticPostWithEmptyBody(EmptyBody)

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/empty"},"headers":{"messageId":["123"],"method":["post"]}},"body":null}"""
      )

      whenReady(f) { r =>
        r shouldBe a[NoContent[_]]
        r.body shouldBe a[EmptyBody]
      }
    }

    "<~ client got exception" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":409,"headers":{"messageId":["abcde12345"]}},"body":{"code":"failed","errorId":"abcde12345"}}"""
      )

      val hyperBus = newHyperBus(ct, null)
      val f = hyperBus <~ TestPost1(TestBody1("ha ha"))

      ct.input should equal(
        """{"request":{"uri":{"pattern":"/resources"},"headers":{"messageId":["123"],"method":["post"],"contentType":["application/vnd+test-1.json"]}},"body":{"resourceData":"ha ha"}}"""
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
          Created(TestCreatedBody("100500"))
        }
      }

      val req = """{"request":{"uri":{"pattern":"/resources"},"headers":{"method":["post"],"contentType":["application/vnd+test-1.json"],"messageId":["123"]}},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = MessageDeserializer.deserializeRequestWith(ba)(st.sInputDeserializer)
      msg should equal(TestPost1(TestBody1("ha ha")))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(Created(TestCreatedBody("100500")))
      }
    }

    "~> static request with empty body (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: StaticPostWithEmptyBody =>
        Future {
          NoContent(EmptyBody)
        }
      }

      val req = """{"request":{"uri":{"pattern":"/empty"},"headers":{"method":["post"],"messageId":["123"]}},"body":null}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = MessageDeserializer.deserializeRequestWith(ba)(st.sInputDeserializer)
      msg should equal(StaticPostWithEmptyBody(EmptyBody))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(NoContent(EmptyBody))
      }
    }

    "~> static request with dynamic body (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: StaticPostWithDynamicBody =>
        Future {
          NoContent(EmptyBody)
        }
      }

      val req = """{"request":{"uri":{"pattern":"/empty"},"headers":{"method":["post"],"contentType":["some-content"],"messageId":["123"]}},"body":"haha"}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = MessageDeserializer.deserializeRequestWith(ba)(st.sInputDeserializer)
      msg should equal(StaticPostWithDynamicBody(DynamicBody(Some("some-content"), Text("haha"))))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r should equal(NoContent(EmptyBody))
      }
    }

    "~> dynamic request (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus.onCommand(RequestMatcher(
        Some(Uri("/test")),
        Map(Header.METHOD → Specific(Method.GET)))
      ) { request =>
        Future {
          NoContent(EmptyBody)
        }
      }

      val req = """{"request":{"uri":{"pattern":"/test"},"headers":{"method":["get"],"contentType":["some-content"],"messageId":["123"]}},"body":"haha"}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = MessageDeserializer.deserializeRequestWith(ba)(st.sInputDeserializer)
      msg should equal(DynamicRequest(
        RequestHeader(Uri("/test"), Map(
          Header.METHOD → Seq(Method.GET),
          Header.CONTENT_TYPE → Seq("some-content"),
          Header.MESSAGE_ID → Seq("123"))
        ),
        DynamicBody(Some("some-content"), Text("haha")))
      )

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r shouldBe a[NoContent[_]]
        r.asInstanceOf[NoContent[_]].body shouldBe a[EmptyBody]
      }
    }

    "<| static request publishing (client)" in {
      val rsp = """{"response":{"status":409,"headers":{"messageId":["123"]}},"body":{"code":"failed","errorId":"abcde12345"}}"""
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
      val futureResult = hyperBus <| TestPost1(TestBody1("ha ha"))
      whenReady(futureResult) { r =>
        sentEvents.size should equal(1)
      }
    }

    "<| dynamic request publishing (client)" in {
      val rsp = """{"response":{"status":409,"headers":{"messageId":["123"]}},"body":{"code":"failed","errorId":"abcde12345"}}"""
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
      val futureResult = hyperBus <| DynamicRequest(Uri("/resources"), Method.POST,
        DynamicBody(Some("application/vnd+test-1.json"), Obj(Map("resourceData" → Text("ha ha")))))
      whenReady(futureResult) { r =>
        sentEvents.size should equal(1)
      }
    }

    "|> static request subscription (server)" in {
      var receivedEvents = 0
      val serverTransport = new ServerTransportTest() {
        override def onEvent(requestMatcher: RequestMatcher,
                             groupName: String,
                             inputDeserializer: RequestDeserializer[Request[Body]])
                            (handler: (Request[Body]) => Future[Unit]): Future[Subscription] = {
          receivedEvents += 1
          super.onEvent(requestMatcher, groupName, inputDeserializer)(handler)
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
        override def onEvent(requestMatcher: RequestMatcher,
                             groupName: String,
                             inputDeserializer: RequestDeserializer[Request[Body]])
                            (handler: (Request[Body]) => Future[Unit]): Future[Subscription] = {
          receivedEvents += 1
          super.onEvent(requestMatcher, groupName, inputDeserializer)(handler)
        }
      }
      val hyperBus = newHyperBus(null, serverTransport)

      hyperBus.onEvent(
        RequestMatcher(
          Some(Uri("/test")),
          Map(Header.METHOD → Specific(Method.GET))),
        Some("group1")
      ) { request: DynamicRequest => Future {} }
      receivedEvents should equal(1)
    }

    "~> (server throw exception)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      hyperBus ~> { post: TestPost1 =>
        Future {
          throw Conflict(ErrorBody("failed", errorId = "abcde12345"))
        }
      }

      val req = """{"request":{"uri":{"pattern":"/resources"},"headers":{"messageId":["123"],"method":["post"],"contentType":["application/vnd+test-1.json"]}},"body":{"resourceData":"ha ha"}}"""
      val ba = new ByteArrayInputStream(req.getBytes("UTF-8"))
      val msg = MessageDeserializer.deserializeRequestWith(ba)(st.sInputDeserializer)
      msg should equal(TestPost1(TestBody1("ha ha")))

      val futureResult = st.sHandler(msg)
      whenReady(futureResult) { r =>
        r shouldBe a[Conflict[_]]
        val ba = new ByteArrayOutputStream()
        r.serialize(ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":409,"headers":{"messageId":["123"]}},"body":{"code":"failed","errorId":"abcde12345"}}"""
        )
      }
    }

    "off test ~> (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      val id1f = hyperBus ~> { post: TestPostWithNoContent =>
        Future {
          NoContent(EmptyBody)
        }
      }
      val id1 = id1f.futureValue

      st.sHandler shouldNot equal(null)
      hyperBus.off(id1).futureValue
      st.sHandler should equal(null)
      st.sSubscriptionId should equal("1")

      val id2f = hyperBus ~> { post: TestPostWithNoContent =>
        Future {
          NoContent(EmptyBody)
        }
      }
      val id2 = id2f.futureValue

      st.sHandler shouldNot equal(null)
      hyperBus.off(id2).futureValue
      st.sHandler should equal(null)
      st.sSubscriptionId should equal("2")
    }

    "off test |> (server)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null, st)
      val id1f = hyperBus |> { request: TestPost1 => Future {} }
      val id1 = id1f.futureValue

      st.sSubscriptionHandler shouldNot equal(null)
      hyperBus.off(id1).futureValue
      st.sSubscriptionHandler should equal(null)
      st.sSubscriptionId should equal("1")

      val id2f = hyperBus |> { request: TestPost1 => Future {} }
      val id2 = id2f.futureValue

      st.sSubscriptionHandler shouldNot equal(null)
      hyperBus.off(id2).futureValue
      st.sSubscriptionHandler should equal(null)
      st.sSubscriptionId should equal("2")
    }
  }

  def newHyperBus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, RequestMatcher(Some(Uri(Any)))))
    val sr = List(TransportRoute(st, RequestMatcher(Some(Uri(Any)))))
    val transportManager = new TransportManager(cr, sr, ExecutionContext.global)
    new HyperBus(transportManager, Some("group1"), logMessages = true)
  }
}
