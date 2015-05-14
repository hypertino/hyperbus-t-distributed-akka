import java.util.concurrent.atomic.AtomicInteger

import eu.inn.servicebus.transport._
import org.scalatest.{Matchers, FreeSpec}
import org.scalatest.concurrent.{ScalaFutures, Futures}
import org.scalatest.time.{Span, Seconds, Millis}
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class InprocTransportTest extends FreeSpec with ScalaFutures with Matchers {

  "InprocTransport " - {
    "Simple Test" in {
      val t = new InprocTransport
      t.on[String,String]("a", null) { s =>
        SubscriptionHandlerResult(Future { s.reverse }, null)
      }

      val f: Future[String] = t.ask("a", "hey", null, null)

      whenReady(f) { s =>
        s should equal ("yeh")
      }
    }

    "NoTransportRouteException Test" in {
      val t = new InprocTransport
      t.on[String,String]("notexists", null) {s  =>
        SubscriptionHandlerResult(Future { s.reverse }, null)
      }

      val f: Future[String] = t.ask("a", "hey", null, null)

      whenReady(f.failed) { e =>
        e shouldBe a [NoTransportRouteException]
      }
    }

    "Complex Test (Service and Subscribers)" in {
      val t = new InprocTransport
      t.on[String,String]("a", null) { s =>
        SubscriptionHandlerResult(Future { s.reverse }, null)
      }

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]
      val group1Func = (s: String) => {
        SubscriptionHandlerResult[Unit](Future {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }, null)
      }

      t.subscribe("a", "group1", DefaultPosition, null)(group1Func)
      t.subscribe("a", "group1", DefaultPosition, null)(group1Func)
      t.subscribe("a", "group1", DefaultPosition, null)(group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]
      val group2Func = (s: String) => {
        SubscriptionHandlerResult[Unit](Future {
          group2.incrementAndGet()
          group2promise.success(Unit)
        },null)
      }

      t.subscribe("a", "group2", DefaultPosition, null)(group2Func)
      t.subscribe("a", "group2", DefaultPosition, null)(group2Func)

      val f: Future[String] = t.ask("a", "hey", null, null)

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
        SubscriptionHandlerResult[Unit](Future {
          group1.incrementAndGet()
          group1promise.success(Unit)
        },
        null)
      }

      t.subscribe("a", "group1", DefaultPosition, null)(group1Func)
      t.subscribe("a", "group1", DefaultPosition, null)(group1Func)
      t.subscribe("a", "group1", DefaultPosition, null)(group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]
      val group2Func = (s: String) => {
        SubscriptionHandlerResult[Unit](Future {
          group2.incrementAndGet()
          group2promise.success(Unit)
        },
        null)
      }

      t.subscribe("a", "group2", DefaultPosition, null)(group2Func)
      t.subscribe("a", "group2", DefaultPosition, null)(group2Func)

      val f: Future[Unit] = t.ask("a", "hey", null, null)

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

      t.on[String,String]("a", null) { s =>
        SubscriptionHandlerResult(Future {
          receivers.incrementAndGet()
          s.reverse
        },null)
      }

      t.on[String,String]("a", null) { s =>
        SubscriptionHandlerResult(Future {
          receivers.incrementAndGet()
          s.reverse
        },null)
      }

      val f1: Future[String] =
        t.ask("a", "hey", null, null)

      val f2: Future[String] =
        t.ask("a", "hey you", null, null)

      val f3: Future[String] =
        t.ask("a", "yo", null, null)

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
      val id1 = t.on[String,String]("a", null) { s =>
        SubscriptionHandlerResult(Future { s.reverse },null)
      }

      val id2 = t.on[String,String]("a", null) { s =>
        SubscriptionHandlerResult(Future { s.reverse },null)
      }

      val f1: Future[String] = t.ask("a", "hey", null, null)

      whenReady(f1) { s =>
        s should equal ("yeh")
      }

      t.off(id1)

      val f2: Future[String] = t.ask("a", "yo", null, null)

      whenReady(f2) { s =>
        s should equal ("oy")
      }

      t.off(id2)

      val f3: Future[String] = t.ask("a", "hey", null, null)

      whenReady(f3.failed) { e =>
        e shouldBe a [NoTransportRouteException]
      }
    }
  }
}
