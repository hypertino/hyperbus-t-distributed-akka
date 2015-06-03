import java.util.concurrent.atomic.AtomicInteger

import eu.inn.servicebus.serialization.PartitionArgsExtractor
import eu.inn.servicebus.transport._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class InprocTransportTest extends FreeSpec with ScalaFutures with Matchers {


  "InprocTransport " - {
    "Simple Test" in {
      val t = new InprocTransport
      t.on[String, String](Topic("a", PartitionArgs(Map())), null, mockExtractor[String]) { s =>
        SubscriptionHandlerResult(Future {
          s.reverse
        }, null)
      }

      val f: Future[String] = t.ask(Topic("a", PartitionArgs(Map())), "hey", null, null)

      whenReady(f) { s =>
        s should equal("yeh")
      }
    }

    "NoTransportRouteException Test" in {
      val t = new InprocTransport
      t.on[String, String](Topic("notexists", PartitionArgs(Map())), null, mockExtractor[String]) { s =>
        SubscriptionHandlerResult(Future {
          s.reverse
        }, null)
      }

      val f: Future[String] = t.ask(Topic("a", PartitionArgs(Map())), "hey", null, null)

      whenReady(f.failed) { e =>
        e shouldBe a[NoTransportRouteException]
      }
    }

    "Complex Test (Service and Subscribers)" in {
      val t = new InprocTransport
      t.on[String, String](Topic("a", PartitionArgs(Map())), null, mockExtractor[String]) { s =>
        SubscriptionHandlerResult(Future {
          s.reverse
        }, null)
      }

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1Func = (s: String) => {
        SubscriptionHandlerResult[Unit](Future {
          group1.incrementAndGet()
          group1promise.success(Unit)
        }, null)
      }

      t.subscribe(Topic("a", PartitionArgs(Map())), "group1", null, mockExtractor[String])(group1Func)
      t.subscribe(Topic("a", PartitionArgs(Map())), "group1", null, mockExtractor[String])(group1Func)
      t.subscribe(Topic("a", PartitionArgs(Map())), "group1", null, mockExtractor[String])(group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2Func = (s: String) => {
        SubscriptionHandlerResult[Unit](Future {
          group2.incrementAndGet()
          group2promise.success(Unit)
        }, null)
      }

      t.subscribe(Topic("a", PartitionArgs(Map())), "group2", null, mockExtractor[String])(group2Func)
      t.subscribe(Topic("a", PartitionArgs(Map())), "group2", null, mockExtractor[String])(group2Func)

      val f: Future[String] = t.ask(Topic("a", PartitionArgs(Map())), "hey", null, null)

      whenReady(f) { s =>
        s should equal("yeh")
      }

      whenReady(group1promise.future) { _ =>
        whenReady(group2promise.future) { _ =>
          Thread.sleep(300)

          group1.get() should equal(1)
          group2.get() should equal(1)
        }
      }
    }

    "Test Subscribers" in {
      val t = new InprocTransport

      val group1 = new AtomicInteger(0)
      val group1promise = Promise[Unit]()
      val group1Func = (s: String) => {
        SubscriptionHandlerResult[Unit](Future {
          group1.incrementAndGet()
          group1promise.success(Unit)
        },
          null)
      }

      t.subscribe(Topic("a", PartitionArgs(Map())), "group1", null, mockExtractor[String])(group1Func)
      t.subscribe(Topic("a", PartitionArgs(Map())), "group1", null, mockExtractor[String])(group1Func)
      t.subscribe(Topic("a", PartitionArgs(Map())), "group1", null, mockExtractor[String])(group1Func)

      val group2 = new AtomicInteger(0)
      val group2promise = Promise[Unit]()
      val group2Func = (s: String) => {
        SubscriptionHandlerResult[Unit](Future {
          group2.incrementAndGet()
          group2promise.success(Unit)
        },
          null)
      }

      t.subscribe(Topic("a", PartitionArgs(Map())), "group2", null, mockExtractor[String])(group2Func)
      t.subscribe(Topic("a", PartitionArgs(Map())), "group2", null, mockExtractor[String])(group2Func)

      val f: Future[Unit] = t.ask(Topic("a", PartitionArgs(Map())), "hey", null, null)

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

      t.on[String, String](Topic("a", PartitionArgs(Map())), null, mockExtractor[String]) { s =>
        SubscriptionHandlerResult(Future {
          receivers.incrementAndGet()
          s.reverse
        }, null)
      }

      t.on[String, String](Topic("a", PartitionArgs(Map())), null, mockExtractor[String]) { s =>
        SubscriptionHandlerResult(Future {
          receivers.incrementAndGet()
          s.reverse
        }, null)
      }

      val f1: Future[String] =
        t.ask(Topic("a", PartitionArgs(Map())), "hey", null, null)

      val f2: Future[String] =
        t.ask(Topic("a", PartitionArgs(Map())), "hey you", null, null)

      val f3: Future[String] =
        t.ask(Topic("a", PartitionArgs(Map())), "yo", null, null)

      whenReady(f1) { s1 =>
        s1 should equal("yeh")
        whenReady(f2) { s2 =>
          s2 should equal("uoy yeh")
          whenReady(f3) { s3 =>
            s3 should equal("oy")
            receivers.get() should equal(3)
          }
        }
      }
    }

    "Unsubscribe Test" in {
      val t = new InprocTransport
      val id1 = t.on[String, String](Topic("a", PartitionArgs(Map())), null, mockExtractor[String]) { s =>
        SubscriptionHandlerResult(Future {
          s.reverse
        }, null)
      }

      val id2 = t.on[String, String](Topic("a", PartitionArgs(Map())), null, mockExtractor[String]) { s =>
        SubscriptionHandlerResult(Future {
          s.reverse
        }, null)
      }

      val f1: Future[String] = t.ask(Topic("a", PartitionArgs(Map())), "hey", null, null)

      whenReady(f1) { s =>
        s should equal("yeh")
      }

      t.off(id1)

      val f2: Future[String] = t.ask(Topic("a", PartitionArgs(Map())), "yo", null, null)

      whenReady(f2) { s =>
        s should equal("oy")
      }

      t.off(id2)

      val f3: Future[String] = t.ask(Topic("a", PartitionArgs(Map())), "hey", null, null)

      whenReady(f3.failed) { e =>
        e shouldBe a[NoTransportRouteException]
      }
    }
  }

  def mockExtractor[T]: PartitionArgsExtractor[T] = {
    (x: T) => PartitionArgs(Map())
  }
}
