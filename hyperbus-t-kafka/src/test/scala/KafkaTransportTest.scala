import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.ConfigFactory
import eu.inn.hyperbus.model.annotations.{body, request}
import eu.inn.hyperbus.model.{Body, DynamicRequest, Method, Request}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfter, FreeSpec, Matchers}
import rx.lang.scala.Subscriber

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

@body("mock")
case class MockBody(test: String) extends Body

@request(Method.POST, "/mock/{partitionId}")
case class MockRequest(partitionId: String, body: MockBody) extends Request[MockBody]

@request(Method.POST, "/mock1/{partitionId}")
case class MockRequest1(partitionId: String, body: MockBody) extends Request[MockBody]

@request(Method.POST, "/mock2/{partitionId}")
case class MockRequest2(partitionId: String, body: MockBody) extends Request[MockBody]

class KafkaTransportTest extends FreeSpec with ScalaFutures with Matchers with BeforeAndAfter with Eventually {
  var transportManager: TransportManager = null
  before {
    val transportConfiguration = TransportConfigurationLoader.fromConfig(ConfigFactory.load())
    transportManager = new TransportManager(transportConfiguration)
  }

  after {
    if (transportManager != null) {
      Await.result(transportManager.shutdown(10.seconds), 10.seconds)
    }
  }

  implicit val defaultPatience = PatienceConfig(timeout =  Span(5, Seconds), interval = Span(50, Millis))

  "KafkaTransport " - {
    "Publish and then Subscribe" in {

      import ExecutionContext.Implicits.global

      val subscriber1 = new Subscriber[MockRequest]() { override def onNext(value: MockRequest): Unit = {} }

      // read previous messages if any
      val fsub1 = transportManager.onEvent[MockRequest](RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub1", MockRequest.apply, subscriber1)
      val fsub2 = transportManager.onEvent[MockRequest](RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub2", MockRequest.apply, subscriber1)
      Thread.sleep(1000)
      val sub1 = fsub1.futureValue
      val sub2 = fsub2.futureValue

      transportManager.off(sub1).futureValue
      transportManager.off(sub2).futureValue

      Thread.sleep(1000)

      val f = Future.sequence(List(
        transportManager.publish(MockRequest("1", MockBody("12345"))),
        transportManager.publish(MockRequest("2", MockBody("12345"))))
      )
      val publishResults = f.futureValue
      publishResults.foreach { publishResult ⇒
        publishResult.sent should equal(Some(true))
        publishResult.offset shouldNot equal(None)
      }

      val cnt = new AtomicInteger(0)

      val fsubs = mutable.MutableList[Future[Subscription]]()
      val subscriber2 = new Subscriber[MockRequest]() {
        override def onNext(value: MockRequest): Unit = {
          value.body.test should equal("12345")
          cnt.incrementAndGet()
        }
      }

      fsubs += transportManager.onEvent[MockRequest](RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub1", MockRequest.apply, subscriber2)
      fsubs += transportManager.onEvent[MockRequest](RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub1", MockRequest.apply, subscriber2)
      fsubs += transportManager.onEvent[MockRequest](RequestMatcher(Some(Uri("/mock/{partitionId}"))), "sub2", MockRequest.apply, subscriber2)

      val subs = fsubs.map(_.futureValue)

      Thread.sleep(1000) // we need to wait until subscriptions will go acros the
      // clear counter
      //cnt.set(0)


      eventually {
        cnt.get should equal(4)
      }
      Thread.sleep(1000) // give chance to increment to another service (in case of wrong implementation)
      cnt.get should equal(4)

      subs.foreach(transportManager.off)
    }
  }

  "Publish and then Subscribe with Same Group/Topic but different URI" in {
    import ExecutionContext.Implicits.global
    Thread.sleep(1000)

    val cnt = new AtomicInteger(0)

    val fsubs = mutable.MutableList[Future[Subscription]]()
    val subscriber1 = new Subscriber[MockRequest1]() {
      override def onNext(value: MockRequest1): Unit = {
        // TODO: fix this dirty hack. Message of type MockRequest1 should not be passed here as an instance of MockRequest2
        if (value.body.test == "12345")
          cnt.incrementAndGet()
      }
    }
    val subscriber2 = new Subscriber[MockRequest2]() {
      override def onNext(value: MockRequest2): Unit = {
        // TODO: fix this dirty hack. Message of type MockRequest1 should not be passed here as an instance of MockRequest2
        if (value.body.test == "54321")
          cnt.incrementAndGet()
      }
    }

    fsubs += transportManager.onEvent[MockRequest1](RequestMatcher(Some(Uri("/mock1/{partitionId}"))), "sub1", MockRequest1.apply, subscriber1)
    fsubs += transportManager.onEvent[MockRequest2](RequestMatcher(Some(Uri("/mock2/{partitionId}"))), "sub1", MockRequest2.apply, subscriber2)
    val subs = fsubs.map(_.futureValue)

    Thread.sleep(1000) // we need to wait until subscriptions will go acros the

    val f = Future.sequence(List(
      transportManager.publish(MockRequest1("1", MockBody("12345"))),
      transportManager.publish(MockRequest2("2", MockBody("54321"))))
    )

    val publishResults = f.futureValue
    publishResults.foreach { publishResult ⇒
      publishResult.sent should equal(Some(true))
      publishResult.offset shouldNot equal(None)
    }

    eventually {
      cnt.get should equal(2)
    }
    Thread.sleep(1000) // give chance to increment to another service (in case of wrong implementation)
    cnt.get should equal(2)

    subs.foreach(transportManager.off)
  }
}

