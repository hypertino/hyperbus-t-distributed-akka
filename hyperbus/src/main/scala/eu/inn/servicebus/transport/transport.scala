package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.impl.Subscriptions
import eu.inn.servicebus.serialization.{Decoder, Encoder}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}
import scala.util.Random

trait ClientTransport {
  def ask[OUT,IN](
                    topic: String,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]
}

case class SubscriptionHandlerResult[OUT](futureResult: Future[OUT],resultEncoder:Encoder[OUT])

trait ServerTransport {
  def on[OUT,IN](topic: String, groupName: Option[String], inputDecoder: Decoder[IN])
                       (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def off(subscriptionId: String)
}

private [transport] case class Subscription[OUT,IN](handler: (IN) => SubscriptionHandlerResult[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

class InprocTransport extends ClientTransport with ServerTransport {
  protected val subscriptions = new Subscriptions[Subscription[_,_]]
  protected val randomGen = new Random()
  protected val log = LoggerFactory.getLogger(this.getClass)

  override def ask[OUT,IN](
                              topic: String,
                              message: IN,
                              inputEncoder: Encoder[IN],
                              outputDecoder: Decoder[OUT]
                              ): Future[OUT] = {
    var result: Future[OUT] = null

    subscriptions.get(topic) foreach { case (groupName,subscrSeq) =>
      val idx = if (subscrSeq.size > 1) {
        randomGen.nextInt(subscrSeq.size)
      } else {
        0
      }

      if (groupName.isEmpty) { // default subscription (groupName="") returns reply
        val subscriber = subscrSeq(idx).subscription.asInstanceOf[Subscription[OUT,IN]]
        result = subscriber.handler(message).futureResult
      } else {
        val subscriber = subscrSeq(idx).subscription.asInstanceOf[Subscription[Unit,IN]]
        subscriber.handler(message)
      }
      if (log.isTraceEnabled) {
        log.trace(s"Message ($message) is delivered to ${subscrSeq(idx).subscriptionId}@$groupName}")
      }

      if (result == null) {
        val p = Promise[Unit]()
        p.success(Unit)
        result = p.future.asInstanceOf[Future[OUT]]
      }
    }

    if (result == null) {
      val p = Promise[OUT]()
      p.failure(new NoTransportRouteException(s"Route to '$topic' isn't found"))
      p.future
    }
    else {
      result
    }
  }

  def on[OUT,IN](topic: String, groupName: Option[String], inputDecoder: Decoder[IN])
                       (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {

    subscriptions.add(topic,groupName,Subscription[OUT,IN](handler))
  }

  def off(subscriptionId: String) = {
    subscriptions.remove(subscriptionId)
  }
}