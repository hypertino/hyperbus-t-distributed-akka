package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.impl.Subscriptions
import eu.inn.servicebus.serialization.{Decoder, Encoder}
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

private [transport] case class Subscription[OUT,IN](handler: IN => Future[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

class InprocTransport extends ClientTransport with ServerTransport {
  protected val subscriptions = new Subscriptions[Subscription[_,_]]
  protected val randomGen = new Random()
  protected val log = LoggerFactory.getLogger(this.getClass)

  override def send[OUT,IN](
                              topic: String,
                              message: IN,
                              outputDecoder: Decoder[OUT],
                              inputEncoder: Encoder[IN]
                              ): Future[OUT] = {
    var result: Future[OUT] = null

    subscriptions.get(topic) foreach { case (groupName,subscrSeq) =>
      val idx = if (subscrSeq.size > 1) {
        randomGen.nextInt(subscrSeq.size)
      } else {
        0
      }

      if (groupName.isEmpty) { // default subscription (groupName="") returns reply
        val subscriber = subscrSeq(idx).subcription.asInstanceOf[Subscription[OUT,IN]]
        result = subscriber.handler(message)
      } else {
        val subscriber = subscrSeq(idx).subcription.asInstanceOf[Subscription[Unit,IN]]
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

    subscriptions.add(topic,groupName,Subscription[OUT,IN](handler))
  }

  def unsubscribe(subscriptionId: String) = {
    subscriptions.remove(subscriptionId)
  }
}