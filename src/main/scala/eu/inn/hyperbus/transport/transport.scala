package eu.inn.hyperbus.transport

import java.util.concurrent.atomic.AtomicLong

import eu.inn.hyperbus.serialization.{Decoder, Encoder}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}
import scala.util.Random

trait ClientTransport {
  def send[OUT,IN](
                    topic: String,
                    message: IN,
                    outputDecoder: Decoder[OUT],
                    inputEncoder: Encoder[IN]
                    ): Future[OUT]
}

trait ServerTransport {
  def subscribe[OUT,IN](
                         topic: String,
                         groupName: Option[String],
                         inputDecoder: Decoder[IN],
                         outputEncoder: Encoder[OUT],
                         handler: IN => Future[OUT]
                         ): String

  def unsubscribe(subscriptionId: String)
}

class NoTransportRouteException(message: String) extends RuntimeException(message)

private [hyperbus] case class Subscriber[OUT,IN](id: String, handler: IN => Future[OUT])

class InprocTransport extends ClientTransport with ServerTransport {
  import scala.collection.concurrent.TrieMap

  val routes = new TrieMap[String, Map[String,IndexedSeq[Subscriber[_,_]]]]
  val subscriptions = new TrieMap[String, String]
  val indexGen = new AtomicLong(0)
  val randomGen = new Random()
  val log = LoggerFactory.getLogger(this.getClass)

  override def send[OUT,IN](
                              topic: String,
                              message: IN,
                              outputDecoder: Decoder[OUT],
                              inputEncoder: Encoder[IN]
                              ): Future[OUT] = {
    var result: Future[OUT] = null

    routes.get(topic) foreach { subscriptions =>
      subscriptions.foreach { kv =>
        val idx = if (kv._2.size > 1) {
          randomGen.nextInt(kv._2.size)
        } else {
          0
        }

        if (kv._1.isEmpty) { // default subscription returns reply
          val subscriber = kv._2(idx).asInstanceOf[Subscriber[OUT,IN]]
          result = subscriber.handler(message)
        } else {
          val subscriber = kv._2(idx).asInstanceOf[Subscriber[Unit,IN]]
          subscriber.handler(message)
        }
        if (log.isTraceEnabled()) {
          log.trace(s"Message ($message) is delivered to ${kv._2(idx).id}@${kv._1}}")
        }
      }

      if (result == null) {
        val p = Promise[Unit]
        p.success(Unit)
        result = p.future.asInstanceOf[Future[OUT]]
      }
    }

    if (result == null) {
      val p = Promise[OUT]
      p.failure(new NoTransportRouteException(s"Topic '$topic' route isn't found"))
      p.future
    }
    else {
      result
    }
  }

  def subscribe[OUT,IN](
                         topic: String,
                         groupName: Option[String],
                         inputDecoder: Decoder[IN],
                         outputEncoder: Encoder[OUT],
                         handler: IN => Future[OUT]
                         ): String = {

    val subscriptionId = indexGen.incrementAndGet().toHexString
    val groupNameStr = groupName.getOrElse("")

    val subscriberSeq = IndexedSeq(Subscriber(subscriptionId, handler))
    this.synchronized {
      val prev = routes.putIfAbsent(
        topic, Map(groupNameStr -> subscriberSeq)
      )

      prev.map { existing =>
        val map =
          if (existing.contains(groupNameStr)) {
            existing.map {
              kv => (
                  kv._1,
                  if (kv._1 == groupNameStr) {
                    kv._2 ++ subscriberSeq
                  } else {
                    kv._2
                  }
                )
            }
          }
          else {
            existing + (groupNameStr -> subscriberSeq)
          }
        routes.put(topic, map)
      }
    }
    subscriptions += subscriptionId -> topic
    subscriptionId
  }

  def unsubscribe(subscriptionId: String) = {
    subscriptions.get(subscriptionId).foreach {
      topic =>
        routes.get(topic).foreach { topicSubscribers =>
          val newTopicSubscribers = topicSubscribers.map { kv =>
            val newSeq = kv._2.filter(s => s.id != subscriptionId)
            if (newSeq.isEmpty)
              None
            else
              Some(kv._1, newSeq)
          }.flatten.toMap

          if (newTopicSubscribers.isEmpty)
            routes.remove(topic)
          else
            routes.put(topic, newTopicSubscribers)
        }
    }
  }
}