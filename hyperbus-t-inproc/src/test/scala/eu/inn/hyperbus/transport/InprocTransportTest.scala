package eu.inn.hyperbus.transport

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

import eu.inn.hyperbus.transport.api._
import eu.inn.hyperbus.transport.api.uri.Uri
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

case class MockRequest(uri: Uri, message: String) extends TransportRequest {
  override def correlationId: String = ???

  override def messageId: String = ???

  override def serialize(output: OutputStream): Unit = output.write(message.getBytes("UTF-8"))
}

object MockRequest {
  def apply(pattern: String, message: String): MockRequest = MockRequest(Uri(pattern), message)
}

case class MockResponse(message: String) extends TransportResponse {
  override def correlationId: String = ???

  override def messageId: String = ???

  override def serialize(output: OutputStream): Unit = output.write(message.getBytes("UTF-8"))
}

class InprocTransportTest extends FreeSpec with ScalaFutures with Matchers {

  //todo: add test for: + handler exception, decoder exception

  "InprocTransport " - {
    "Simple Test" in {
      val t = new InprocTransport
      t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          MockResponse(msg.message.reverse)
        }
      }

      val f: Future[MockResponse] = t.ask(MockRequest("a", "hey"), null)

      whenReady(f) { m =>
        m.message should equal("yeh")
      }
    }

    "NoTransportRouteException Test" in {
      val t = new InprocTransport
      t.process(Uri("notexists"), null, null) { msg: MockRequest =>
        Future {
          MockResponse(msg.message.reverse)
        }
      }

      val f: Future[MockResponse] = t.ask(MockRequest("a", "hey"), null)

      whenReady(f.failed) { e =>
        e shouldBe a[NoTransportRouteException]
      }
    }

    "Complex ask Test (Service and Subscribers)" in {
      val t = new InprocTransport
      t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          MockResponse(msg.message.reverse)
        }
      }

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1Func = (msg: MockRequest) => {
        Future[Unit] {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }
      }

      t.subscribe(Uri("a"), "group1", null)(group1Func)
      t.subscribe(Uri("a"), "group1", null)(group1Func)
      t.subscribe(Uri("a"), "group1", null)(group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2Func = (msg: MockRequest) => {
        Future[Unit] {
          group2.incrementAndGet()
          group2promise.success(Unit)
        }
      }

      t.subscribe(Uri("a"), "group2", null)(group2Func)
      t.subscribe(Uri("a"), "group2", null)(group2Func)

      val f: Future[MockResponse] = t.ask(MockRequest("a", "hey"), null)

      whenReady(f) { m =>
        m.message should equal("yeh")
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
      t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          processor.incrementAndGet()
          MockResponse(msg.message.reverse)
        }
      }
      t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          processor.incrementAndGet()
          MockResponse(msg.message.reverse)
        }
      }

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1Func = (msg: MockRequest) => {
        Future[Unit] {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }
      }

      t.subscribe(Uri("a"), "group1", null)(group1Func)
      t.subscribe(Uri("a"), "group1", null)(group1Func)
      t.subscribe(Uri("a"), "group1", null)(group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2Func = (msg: MockRequest) => {
        Future[Unit] {
          group2.incrementAndGet()
          group2promise.success(Unit)
        }
      }

      t.subscribe(Uri("a"), "group2", null)(group2Func)
      t.subscribe(Uri("a"), "group2", null)(group2Func)

      val f: Future[PublishResult] = t.publish(MockRequest("a", "hey"))

      whenReady(f) { publishResult =>
        publishResult.sent should equal(Some(true))
        publishResult.offset should equal(None)
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

    "Test Subscribers" in {
      val t = new InprocTransport

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1Func = (msg: MockRequest) => {
        Future[Unit] {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }
      }

      t.subscribe(Uri("a"), "group1", null)(group1Func)
      t.subscribe(Uri("a"), "group1", null)(group1Func)
      t.subscribe(Uri("a"), "group1", null)(group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2Func = (msg: MockRequest) => {
        Future[Unit] {
          group2.incrementAndGet()
          group2promise.success(Unit)
        }
      }

      t.subscribe(Uri("a"), "group2", null)(group2Func)
      t.subscribe(Uri("a"), "group2", null)(group2Func)

      val f: Future[PublishResult] = t.publish(MockRequest("a", "hey"))

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

      t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          receivers.incrementAndGet()
          MockResponse(msg.message.reverse)
        }
      }

      t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          receivers.incrementAndGet()
          MockResponse(msg.message.reverse)
        }
      }

      val f1: Future[MockResponse] =
        t.ask(MockRequest("a", "hey"), null)

      val f2: Future[MockResponse] =
        t.ask(MockRequest("a", "hey you"), null)

      val f3: Future[MockResponse] =
        t.ask(MockRequest("a", "yo"), null)

      whenReady(f1) { m1 =>
        m1.message should equal("yeh")
        whenReady(f2) { m2 =>
          m2.message should equal("uoy yeh")
          whenReady(f3) { m3 =>
            m3.message should equal("oy")
            receivers.get() should equal(3)
          }
        }
      }
    }

    "Unsubscribe Test" in {
      val t = new InprocTransport
      val id1 = t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          MockResponse(msg.message.reverse)
        }
      }

      val id2 = t.process(Uri("a"), null, null) { msg: MockRequest =>
        Future {
          MockResponse(msg.message.reverse)
        }
      }

      val f1: Future[MockResponse] = t.ask(MockRequest("a", "hey"), null)

      whenReady(f1) { m =>
        m.message should equal("yeh")
      }

      t.off(id1)

      val f2: Future[MockResponse] = t.ask(MockRequest("a", "yo"), null)

      whenReady(f2) { m =>
        m.message should equal("oy")
      }

      t.off(id2)

      val f3: Future[MockResponse] = t.ask(MockRequest("a", "hey"), null)

      whenReady(f3.failed) { e =>
        e shouldBe a[NoTransportRouteException]
      }
    }
  }
}
