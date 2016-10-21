package eu.inn.hyperbus.transport

import java.util.concurrent.atomic.AtomicInteger

import eu.inn.hyperbus.model._
import eu.inn.hyperbus.model.annotations.{body, request, response}
import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.matchers.RequestMatcher
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}
import rx.lang.scala.{Observer, Subscriber}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

@body("mock")
case class MockBody(test: String) extends Body

@request(Method.POST, "/mock")
case class MockRequest(body: MockBody) extends Request[MockBody]

@response(200)
case class MockResponse[+B <: MockBody](body: B) extends Response[B]

class InprocTransportTest extends FreeSpec with ScalaFutures with Matchers {

  //todo: add test for: + handler exception, decoder exception

  "InprocTransport " - {
    "Simple Test" in {
      val t = new InprocTransport
      t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      val f = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      whenReady(f) { m =>
        m.body.test should equal("yeh")
      }
    }

    "NoTransportRouteException Test" in {
      val t = new InprocTransport
      t.onCommand(RequestMatcher(Some(Uri("notexists"))), null) { msg: MockRequest =>
        Future {
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      val f = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      whenReady(f.failed) { e =>
        e shouldBe a[NoTransportRouteException]
      }
    }

    "Complex ask Test (Service and Subscribers)" in {
      val t = new InprocTransport
      t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1subscriber = new Subscriber[Request[Body]] {
        override def onNext(value: Request[Body]): Unit = {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }
      }

      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1subscriber)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1subscriber)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1subscriber)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2subscriber = new Subscriber[Request[Body]] {
        override def onNext(value: Request[Body]): Unit = {
          group2.incrementAndGet()
          group2promise.success(Unit)
        }
      }

      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group2", null, group2subscriber)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group2", null, group2subscriber)

      val f = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      whenReady(f) { m =>
        m.body.test should equal("yeh")
      }

      whenReady(group1promise.future) { _ =>
        whenReady(group2promise.future) { _ =>
          Thread.sleep(300)

          group1.get() should equal(1)
          group2.get() should equal(1)
        }
      }
    }

    "Complex publish Test (Service and Subscribers)" in {
      val t = new InprocTransport

      val processor = new AtomicInteger(0)
      val onCommandFuture1 = t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          processor.incrementAndGet()
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }
      val onCommandFuture2 = t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          processor.incrementAndGet()
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1observer = new Observer[Request[Body]] {
        override def onNext(value: Request[Body]): Unit = {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }
      }

      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1observer)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1observer)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1observer)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2subscriber = new Observer[Request[Body]] {
        override def onNext(value: Request[Body]): Unit = {
          group2.incrementAndGet()
          group2promise.success(Unit)
        }
      }

      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group2", null, group2subscriber)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group2", null, group2subscriber)

      val f: Future[PublishResult] = t.publish(MockRequest(MockBody("hey")))

      whenReady(f) { publishResult =>
        publishResult.sent should equal(Some(true))
        publishResult.offset should equal(None)
        whenReady(onCommandFuture1) { _ ⇒
          processor.get() should equal(1)
          whenReady(group1promise.future) { _ =>
            whenReady(group2promise.future) { _ =>
              Thread.sleep(300)
              group1.get() should equal(1)
              group2.get() should equal(1)
            }
          }
        }
      }
    }

    "Test Subscribers" in {
      val t = new InprocTransport

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1subscriber = new Subscriber[Request[Body]] {
        override def onNext(value: Request[Body]): Unit = {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }
      }

      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1subscriber)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1subscriber)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group1", null, group1subscriber)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2subscriber = new Subscriber[Request[Body]] {
        override def onNext(value: Request[Body]): Unit = {
          group2.incrementAndGet()
          group2promise.success(Unit)
        }
      }

      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group2", null, group2subscriber)
      t.onEvent(RequestMatcher(Some(Uri("/mock"))), "group2", null, group2subscriber)

      val f: Future[PublishResult] = t.publish(MockRequest(MockBody("hey")))

      whenReady(f) { PublishResult =>
        whenReady(group1promise.future) { _ =>
          whenReady(group2promise.future) { _ =>
            Thread.sleep(300)

            group1.get() should equal(1)
            group2.get() should equal(1)
          }
        }
      }
    }

    "Test Receivers" in {
      val t = new InprocTransport
      val receivers = new AtomicInteger(0)

      t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          receivers.incrementAndGet()
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          receivers.incrementAndGet()
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      val f1 =
        t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      val f2 =
        t.ask(MockRequest(MockBody("hey your")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      val f3 =
        t.ask(MockRequest(MockBody("yo")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      whenReady(f1) { m1 =>
        m1.body.test should equal("yeh")
        whenReady(f2) { m2 =>
          m2.body.test should equal("ruoy yeh")
          whenReady(f3) { m3 =>
            m3.body.test should equal("oy")
            receivers.get() should equal(3)
          }
        }
      }
    }

    "Unsubscribe Test" in {
      val t = new InprocTransport
      val id1Future = t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      val id2Future = t.onCommand(RequestMatcher(Some(Uri("/mock"))), null) { msg: MockRequest =>
        Future {
          MockResponse(MockBody(msg.body.test.reverse))
        }
      }

      val id1 = id1Future.futureValue
      val id2 = id2Future.futureValue

      val f1 = t.ask(MockRequest(MockBody("hey")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      whenReady(f1) { m =>
        m.body.test should equal("yeh")
      }

      t.off(id1).futureValue

      val f2 = t.ask(MockRequest(MockBody("yo")), null).asInstanceOf[Future[MockResponse[MockBody]]]

      whenReady(f2) { m =>
        m.body.test should equal("oy")
      }

      t.off(id2).futureValue

      val f3: Future[_] = t.ask(MockRequest(MockBody("hey")), null)

      whenReady(f3.failed) { e =>
        e shouldBe a[NoTransportRouteException]
      }
    }
  }
}
