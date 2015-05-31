package eu.inn.servicebus.transport

import java.util.concurrent.atomic.AtomicLong

import eu.inn.servicebus.serialization._
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait PublishResult {
}

trait PartitionArg {
  def matchArg(arg: PartitionArg): Boolean
}

case object AnyValue extends PartitionArg {
  def matchArg(arg: PartitionArg) = true
}

case class ExactValue(value: String) extends PartitionArg {
  def matchArg(arg: PartitionArg) = arg match {
    case ExactValue(argValue) ⇒ argValue == value
    case AnyValue ⇒ true
    case _ ⇒ false
  }
}

// case class ExactPartition(partition: String) extends PartitionArg -- kafka?
// case class RegExValue(regex: String) extends PartitionValue

case class PartitionArgs(args: Map[String, PartitionArg]) {
  def matchArgs(other: PartitionArgs): Boolean = {
    args.map { case (k, v) ⇒
      other.args.get(k).map { av ⇒
        av.matchArg(v)
      } getOrElse {
        v == AnyValue
      }
    }.forall(r => r)
  }
}

case class Topic(url: String, partitionArgs: PartitionArgs)

trait ClientTransport {
  def ask[OUT, IN](
                    topic: Topic,
                    message: IN,
                    inputEncoder: Encoder[IN],
                    outputDecoder: Decoder[OUT]
                    ): Future[OUT]

  def publish[IN](
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[PublishResult]
}

case class SubscriptionHandlerResult[OUT](futureResult: Future[OUT], resultEncoder: Encoder[OUT])

trait ServerTransport {
  def on[OUT, IN](topic: Topic, inputDecoder: Decoder[IN], partitionArgsExtractor: PartitionArgsExtractor[IN])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: Topic, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)
}

private[transport] case class SubKey(groupName: Option[String], partitionArgs: PartitionArgs)

private[transport] case class Subscription[OUT, IN](
                                                     partitionArgsExtractor: PartitionArgsExtractor[IN],
                                                     handler: (IN) => SubscriptionHandlerResult[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

class InprocTransport(implicit val executionContext: ExecutionContext) extends ClientTransport with ServerTransport {
  protected val subscriptions = new Subscriptions[SubKey, Subscription[_, _]]
  protected val log = LoggerFactory.getLogger(this.getClass)
  protected val currentMessageId = new AtomicLong(System.currentTimeMillis())

  override def ask[OUT, IN](
                             topic: Topic,
                             message: IN,
                             inputEncoder: Encoder[IN],
                             outputDecoder: Decoder[OUT]
                             ): Future[OUT] = {
    var result: Future[OUT] = null

    // todo: filter is redundant for inproc?
    subscriptions.get(topic.url).subRoutes filter (_._1.partitionArgs.matchArgs(topic.partitionArgs)) foreach {
      case (subKey, subscriptionList) =>

        if (subKey.groupName.isEmpty) {
          // default subscription (groupName="") returns reply
          val subscriber = subscriptionList.getRandomSubscription.asInstanceOf[Subscription[OUT, IN]]
          val args = subscriber.partitionArgsExtractor(message)
          if (subKey.partitionArgs.matchArgs(args)) {
            // todo: log if not matched?
            result = subscriber.handler(message).futureResult
            if (log.isTraceEnabled) {
              log.trace(s"Message ($message) is delivered to `on` @$subKey}")
            }
          }
        } else {
          val subscriber = subscriptionList.getRandomSubscription.asInstanceOf[Subscription[Unit, IN]]
          val args = subscriber.partitionArgsExtractor(message)
          if (subKey.partitionArgs.matchArgs(args)) {
            // todo: log if not matched?
            subscriber.handler(message)
            if (result == null) {
              result = Future.successful({}.asInstanceOf[OUT])
            }
            if (log.isTraceEnabled) {
              log.trace(s"Message ($message) is delivered to `subscriber` @$subKey}")
            }
          }
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
                   topic: Topic,
                   message: IN,
                   inputEncoder: Encoder[IN]
                   ): Future[PublishResult] = {
    ask[Any, IN](topic, message, inputEncoder, null) map { x =>
      new PublishResult {
        def messageId: String = currentMessageId.incrementAndGet().toHexString // todo is this really needed?
      }
    }
  }

  def on[OUT, IN](topic: Topic, inputDecoder: Decoder[IN], partitionArgsExtractor: PartitionArgsExtractor[IN])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String = {
    subscriptions.add(
      topic.url,
      SubKey(None, topic.partitionArgs),
      Subscription[OUT, IN](partitionArgsExtractor, handler)
    )
  }

  def subscribe[IN](topic: Topic,
                    groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String = {
    subscriptions.add(
      topic.url,
      SubKey(Some(groupName), topic.partitionArgs),
      Subscription[Unit, IN](partitionArgsExtractor, handler)
    )
  }

  def off(subscriptionId: String) = {
    subscriptions.remove(subscriptionId)
  }
}