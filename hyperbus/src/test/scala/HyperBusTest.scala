import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import eu.inn.binders.dynamic.{Text, Obj}
import eu.inn.hyperbus.HyperBus
import eu.inn.hyperbus.rest._
import eu.inn.hyperbus.rest.standard.{DynamicPost, Conflict, Created}
import eu.inn.hyperbus.serialization.{ResponseBodyDecoder, ResponseHeader}
import eu.inn.servicebus.{TransportRoute, ServiceBus}
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
}

class ServerTransportTest extends ServerTransport {
  var sInputDecoder: Decoder[Any] = null
  var sHandler: (Any) ⇒ SubscriptionHandlerResult[Any] = null
  var sExtractor: PartitionArgsExtractor[Any] = null

  def on[OUT, IN](topic: Topic,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    sInputDecoder = inputDecoder
    sHandler = handler.asInstanceOf[(Any) ⇒ SubscriptionHandlerResult[Any]]
    sExtractor = partitionArgsExtractor.asInstanceOf[PartitionArgsExtractor[Any]]
    ""
  }

  def off(subscriptionId: String) = ???

  //todo: test this
  def subscribe[IN](topic: Topic,
                    groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) ⇒ SubscriptionHandlerResult[Unit]): String = {

    sInputDecoder = inputDecoder
    sHandler = handler.asInstanceOf[(Any) ⇒ SubscriptionHandlerResult[Any]]
    ""
  }
}

class HyperBusTest extends FreeSpec with ScalaFutures with Matchers {
  "HyperBus " - {

    "TEMPORARY" in { // todo: remove this test
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"contentType":"application/vnd+created-body.json"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val request = DynamicPost("/resources",
        DynamicBody(
          Obj(Map("resourceData" → Text("ha ha"))),
          Some("application/vnd+test-1.json")
        )
      )
      val hyperBus = newHyperBus(ct,null)

      import eu.inn.hyperbus.impl.Helpers._
      import eu.inn.hyperbus.{serialization=>hbs}

      val decoder = hyperBus.responseDecoder(
        _: hbs.ResponseHeader,
        _: com.fasterxml.jackson.core.JsonParser,
        {case _ ⇒ hbs.createResponseBodyDecoder[DynamicBody]}
      )

      val f = hyperBus.ask(request,
        encodeDynamicRequest,
        extractDynamicPartitionArgs,
        decoder)

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r.body should equal(DynamicBody(Text("100500")))
      }
    }


    "Send (serialize)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"contentType":"application/vnd+created-body.json"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = newHyperBus(ct,null)
      val f = hyperBus ? TestPost1(TestBody1("ha ha"))

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r.body should equal(TestCreatedBody("100500"))
      }
    }

    /*"Send dynamic (serialize)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":201,"contentType":"application/vnd+created-body.json"},"body":{"resourceId":"100500","_links":{"location":{"href":"/resources/{resourceId}","templated":true}}}}"""
      )

      val hyperBus = newHyperBus(ct,null)
      val f = hyperBus ? DynamicPost("/resources",
        DynamicBody(
          Obj(Map("resourceData" → Text("ha ha"))),
          Some("application/vnd+test-1.json")
        )
      )

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f) { r =>
        r.body should equal(DynamicBody(Text("100500")))
      }
    }*/
    
    "Send (serialize exception)" in {
      val ct = new ClientTransportTest(
        """{"response":{"status":409},"body":{"code":"failed","errorId":"abcde12345"}}"""
      )

      val hyperBus = newHyperBus(ct,null)
      val f = hyperBus ? TestPost1(TestBody1("ha ha"))

      ct.input should equal(
        """{"request":{"url":"/resources","method":"post","contentType":"application/vnd+test-1.json"},"body":{"resourceData":"ha ha"}}"""
      )

      whenReady(f.failed) { r =>
        r should equal(Conflict(ErrorBody("failed", errorId = "abcde12345")))
      }
    }

    "Subscribe (serialize)" in {
      val st = new ServerTransportTest()
      val hyperBus = newHyperBus(null,st)
      hyperBus.on[TestPost1] { post =>
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
      val hyperBus = newHyperBus(null,st)
      hyperBus.on[TestPost1] { post =>
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
        r shouldBe a[Conflict[_]]
        val ba = new ByteArrayOutputStream()
        hres.resultEncoder(r, ba)
        val s = ba.toString("UTF-8")
        s should equal(
          """{"response":{"status":409},"body":{"code":"failed","errorId":"abcde12345"}}"""
        )
      }
    }
  }

  def newHyperBus(ct: ClientTransport, st: ServerTransport) = {
    val cr = List(TransportRoute(ct, AnyArg))
    val sr = List(TransportRoute(st, AnyArg))
    val serviceBus = new ServiceBus(cr, sr)
    new HyperBus(serviceBus)
  }
}
