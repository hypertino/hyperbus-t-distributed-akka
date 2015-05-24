package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.serialization.{Decoder, Encoder}
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait PublishResult {
  def messageId: String
}

trait ClientTransport {
  def ask[OUT,IN](
                    topic: String,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]
  def publish[IN](
                   topic: String,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[PublishResult]
}

case class SubscriptionHandlerResult[OUT](futureResult: Future[OUT],resultEncoder:Encoder[OUT])

trait ServerTransport {
  def on[OUT,IN](topic: String, inputDecoder: Decoder[IN])
                       (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: String, groupName: String, inputDecoder: Decoder[IN])
                (handler: (IN) => SubscriptionHandlerResult[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)
}

private [transport] case class Subscription[OUT,IN](handler: (IN) => SubscriptionHandlerResult[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

class InprocTransport(implicit val executionContext: ExecutionContext) extends ClientTransport with ServerTransport {
  protected val subscriptions = new Subscriptions[Subscription[_,_]]
  protected val randomGen = new Random()
  protected val log = LoggerFactory.getLogger(this.getClass)
  protected val currentMessageId = new AtomicLong(System.currentTimeMillis())

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
        result = Future.successful({}.asInstanceOf[OUT])
      }
    }

    if (result == null) {
      Future.failed[OUT](new NoTransportRouteException(s"Route to '$topic' isn't found"))
    }
    else {
      result
    }
  }

  def publish[IN](
                   topic: String,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[PublishResult] = {
    ask[Any,IN](topic, message, inputEncoder, null) map { x =>
      new PublishResult {
        override def messageId: String = currentMessageId.incrementAndGet().toHexString
      }
    }
  }

  def on[OUT,IN](topic: String, inputDecoder: Decoder[IN])
                       (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {
    subscriptions.add(topic,None,Subscription[OUT,IN](handler))
  }

  def subscribe[IN](topic: String, groupName: String, inputDecoder: Decoder[IN])
                (handler: (IN) => SubscriptionHandlerResult[Unit]): String = {
    subscriptions.add(topic,Some(groupName),Subscription[Unit,IN](handler))
  }

  def off(subscriptionId: String) = {
    subscriptions.remove(subscriptionId)
  }
}