import java.util.concurrent.atomic.AtomicInteger

import eu.inn.servicebus.transport.{HandlerResult, NoTransportRouteException, InprocTransport}
import org.scalatest.{Matchers, FreeSpec}
import org.scalatest.concurrent.{ScalaFutures, Futures}
import org.scalatest.time.{Span, Seconds, Millis}
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class InprocTransportTest extends FreeSpec with ScalaFutures with Matchers {

  "InprocTransport " - {
    "Simple Test" in {
      val t = new InprocTransport
      t.subscribe("a", None, null, (s: String) => {
        HandlerResult(Future { s.reverse }, null)
      })

      val f: Future[String] = t.send("a", "hey", null, null)

      whenReady(f) { s =>
        s should equal ("yeh")
      }
    }

    "NoTransportRouteException Test" in {
      val t = new InprocTransport
      t.subscribe("notexists", None, null, (s: String) => {
        HandlerResult(Future { s.reverse }, null)
      })

      val f: Future[String] = t.send("a", "hey", null, null)

      whenReady(f.failed) { e =>
        e shouldBe a [NoTransportRouteException]
      }
    }

    "Complex Test (Service and Subscribers)" in {
      val t = new InprocTransport
      t.subscribe("a", None, null, (s: String) => {
        HandlerResult(Future { s.reverse }, null)
      })

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]
      val group1Func = (s: String) => {
        HandlerResult(Future {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }, null)
      }

      t.subscribe("a", Some("group1"), null, group1Func)
      t.subscribe("a", Some("group1"), null, group1Func)
      t.subscribe("a", Some("group1"), null, group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]
      val group2Func = (s: String) => {
        HandlerResult(Future {
          group2.incrementAndGet()
          group2promise.success(Unit)
        },null)
      }

      t.subscribe("a", Some("group2"), null, group2Func)
      t.subscribe("a", Some("group2"), null, group2Func)

      val f: Future[String] = t.send("a", "hey", null, null)

      whenReady(f) { s =>
        s should equal ("yeh")
      }

      whenReady(group1promise.future) { _ =>
        whenReady(group2promise.future) { _ =>
          Thread.sleep(300)

          group1.get() should equal (1)
          group2.get() should equal (1)
        }
      }
    }

    "Test Subscribers" in {
      val t = new InprocTransport

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]
      val group1Func = (s: String) => {
        HandlerResult(Future {
          group1.incrementAndGet()
          group1promise.success(Unit)
        },
        null)
      }

      t.subscribe("a", Some("group1"), null, group1Func)
      t.subscribe("a", Some("group1"), null, group1Func)
      t.subscribe("a", Some("group1"), null, group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]
      val group2Func = (s: String) => {
        HandlerResult(Future {
          group2.incrementAndGet()
          group2promise.success(Unit)
        },
        null)
      }

      t.subscribe("a", Some("group2"), null, group2Func)
      t.subscribe("a", Some("group2"), null, group2Func)

      val f: Future[Unit] = t.send("a", "hey", null, null)

      whenReady(f) { _ =>
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

      t.subscribe("a", None, null, (s: String) => {
        HandlerResult(Future {
          receivers.incrementAndGet()
          s.reverse
        },null)
      })

      t.subscribe("a", None, null, (s: String) => {
        HandlerResult(Future {
          receivers.incrementAndGet()
          s.reverse
        },null)
      })

      val f1: Future[String] =
        t.send("a", "hey", null, null)

      val f2: Future[String] =
        t.send("a", "hey you", null, null)

      val f3: Future[String] =
        t.send("a", "yo", null, null)

      whenReady(f1) { s1 =>
        s1 should equal ("yeh")
        whenReady(f2) { s2 =>
          s2 should equal("uoy yeh")
          whenReady(f3) { s3 =>
            s3 should equal("oy")
            receivers.get() should equal (3)
          }
        }
      }
    }

    "Unsubscribe Test" in {
      val t = new InprocTransport
      val id1 = t.subscribe("a", None, null, (s: String) => {
        HandlerResult(Future { s.reverse },null)
      })

      val id2 = t.subscribe("a", None, null, (s: String) => {
        HandlerResult(Future { s.reverse },null)
      })

      val f1: Future[String] = t.send("a", "hey", null, null)

      whenReady(f1) { s =>
        s should equal ("yeh")
      }

      t.unsubscribe(id1)

      val f2: Future[String] = t.send("a", "yo", null, null)

      whenReady(f2) { s =>
        s should equal ("oy")
      }

      t.unsubscribe(id2)

      val f3: Future[String] = t.send("a", "hey", null, null)

      whenReady(f3.failed) { e =>
        e shouldBe a [NoTransportRouteException]
      }
    }
  }
}
