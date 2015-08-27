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

trait Filter {
  def matchArg(other: String): Boolean
  def matchFilter(other: Filter): Boolean
}

case object AllowAny extends Filter {
  def matchArg(other: String) = true
  def matchFilter(other: Filter) = true
}

case class AllowSpecific(value: String) extends Filter {
  def matchArg(other: String) = other == value
  def matchFilter(other: Filter) = other match {
    case AllowSpecific(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchFilter(this)
  }
}

case class AllowRegex(value: String) extends Filter {
  lazy val valueRegex = new Regex(value)
  def matchArg(other: String) = valueRegex.findFirstMatchIn(other).isDefined
  def matchFilter(other: Filter) = other match {
    case AllowSpecific(otherValue) ⇒ matchArg(otherValue)
    case AllowRegex(otherRegexValue) ⇒ otherRegexValue == value
    case _ ⇒ other.matchFilter(this)
  }
}

// case class ExactPartition(partition: String) extends PartitionArg -- kafka?
// todo: rename this class!
case class Filters(filterMap: Map[String, Filter]) {
  def matchArgs(arguments: Map[String,String]): Boolean = {
    filterMap.map { case (k, v) ⇒
      arguments.get(k).map { av ⇒
        v.matchArg(av)
      } getOrElse {
        v == AllowAny
      }
    }.forall(r => r)
  }

  def matchFilters(other:Filters): Boolean = {
    filterMap.map { case (k, v) ⇒
      other.filterMap.get(k).map { av ⇒
        v.matchFilter(av)
      } getOrElse {
        v == AllowAny
      }
    }.forall(r => r)
  }
}

case object Filters {
  val empty = Filters(Map.empty)
}

case class TopicFilter(url: String, valueFilters: Filters = Filters.empty) { // todo: url String -> Filter, url -> template?
  override def toString = s"TopicFilter($url$valueFiltersFormat)"
  private def valueFiltersFormat = if(valueFilters.filterMap.isEmpty) "" else
    valueFilters.filterMap.mkString("#",",","")
}

case class Topic(url: String, values: Map[String,String] = Map.empty[String,String]) { // todo: url String -> Filter, url -> template?, value -> filterValues?
  override def toString = s"Topic($url$valuesFormat)"
  private def valuesFormat = if(values.isEmpty) "" else
    values.mkString("#",",","")
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
  def process[OUT, IN](topic: TopicFilter,
                  inputDecoder: Decoder[IN],
                  partitionArgsExtractor: FilterArgsExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: TopicFilter, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: FilterArgsExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)
  def shutdown(duration: FiniteDuration): Future[Boolean]
}

private[transport] case class SubKey(groupName: Option[String], partitionArgs: Filters)

private[transport] case class Subscription[OUT, IN](inputDecoder: Decoder[IN],
                                                     partitionArgsExtractor: FilterArgsExtractor[IN],
                                                     exceptionEncoder: Encoder[Throwable],
                                                     handler: (IN) => SubscriptionHandlerResult[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

