package eu.inn.servicebus.transport

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.typesafe.config.Config
import eu.inn.servicebus.serialization._
import eu.inn.servicebus.util.ConfigUtils._
import eu.inn.servicebus.util.Subscriptions
import org.slf4j.LoggerFactory

import scala.Option
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex

trait PartitionArg {
  def matchArg(arg: PartitionArg): Boolean
}

case object AnyArg extends PartitionArg {
  def matchArg(arg: PartitionArg) = true
}

case class ExactArg(value: String) extends PartitionArg {
  def matchArg(other: PartitionArg) = other match {
    case ExactArg(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchArg(this)
  }
}

case class RegexArg(value: String) extends PartitionArg {
  lazy val valueRegex = new Regex(value)
  def matchArg(other: PartitionArg) = other match {
    case ExactArg(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexArg(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchArg(this)
  }
}

// case class ExactPartition(partition: String) extends PartitionArg -- kafka?

case class PartitionArgs(args: Map[String, PartitionArg]) {
  def matchArgs(other: PartitionArgs): Boolean = {
    args.map { case (k, v) ⇒
      other.args.get(k).map { av ⇒
        av.matchArg(v)
      } getOrElse {
        v == AnyArg
      }
    }.forall(r => r)
  }
}

case class Topic(url: String, partitionArgs: PartitionArgs) {
  override def toString() = s"Topic($url$formatPartitionArgs)"
  private def formatPartitionArgs = if(partitionArgs.args.isEmpty) "" else
    partitionArgs.args.mkString("#",",","")
}

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
                   ): Future[Unit]

  def shutdown(duration: FiniteDuration): Future[Boolean]
}

case class SubscriptionHandlerResult[OUT](futureResult: Future[OUT], resultEncoder: Encoder[OUT])

trait ServerTransport {
  def process[OUT, IN](topic: Topic,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: PartitionArgsExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: Topic, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: PartitionArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)
  def shutdown(duration: FiniteDuration): Future[Boolean]
}

private[transport] case class SubKey(groupName: Option[String], partitionArgs: PartitionArgs)

private[transport] case class Subscription[OUT, IN](inputDecoder: Decoder[IN],
                                                     partitionArgsExtractor: PartitionArgsExtractor[IN],
                                                     exceptionEncoder: Encoder[Throwable],
                                                     handler: (IN) => SubscriptionHandlerResult[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

