package eu.inn.servicebus.transport

import eu.inn.servicebus.serialization._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex
import scala.reflect.runtime.universe._

sealed trait Filter {
  def matchFilter(other: Filter): Boolean
  def specific: String = this match {
    case SpecificValue(value) ⇒ value
    case _ ⇒ throw new UnsupportedOperationException(s"Specific value expected but got $getClass")
  }
}

case object AnyValue extends Filter {
  def matchFilter(other: Filter) = true
}

case class SpecificValue(value: String) extends Filter {
  def matchFilter(other: Filter) = other match {
    case SpecificValue(otherValue) ⇒ otherValue == value
    case _ ⇒ other.matchFilter(this)
  }
}

case class RegexFilter(value: String) extends Filter {
  lazy val valueRegex = new Regex(value)
  def matchFilter(other: Filter) = other match {
    case SpecificValue(otherValue) ⇒ valueRegex.findFirstMatchIn(otherValue).isDefined
    case RegexFilter(otherRegexValue) ⇒ otherRegexValue == value
    case _ ⇒ other.matchFilter(this)
  }
}

// case class ExactPartition(partition: String) extends PartitionArg -- kafka?
// todo: rename this class!
case class Filters(filterMap: Map[String, Filter]) {
  def matchFilters(other:Filters): Boolean = {
    filterMap.map { case (k, v) ⇒
      other.filterMap.get(k).map { av ⇒
        v.matchFilter(av)
      } getOrElse {
        v == AnyValue
      }
    }.forall(r => r)
  }
}

case object Filters {
  val empty = Filters(Map.empty)
}

case class Topic(urlFilter: Filter, valueFilters: Filters = Filters.empty) {
  override def toString = s"Topic($urlFilter$valueFiltersFormat)"
  private def valueFiltersFormat = if(valueFilters.filterMap.isEmpty) "" else
    valueFilters.filterMap.mkString("#",",","")
}

object Topic {
  def apply(url: String): Topic = Topic(SpecificValue(url), Filters.empty)
  def apply(url: String, valueFilters: Filters): Topic = Topic(SpecificValue(url), valueFilters)
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
                  partitionArgsExtractor: FiltersExtractor[IN],
                  exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => SubscriptionHandlerResult[OUT]): String

  def subscribe[IN](topic: Topic, groupName: String,
                    inputDecoder: Decoder[IN],
                    partitionArgsExtractor: FiltersExtractor[IN])
                   (handler: (IN) => SubscriptionHandlerResult[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)
  def shutdown(duration: FiniteDuration): Future[Boolean]
}

private[transport] case class SubKey(groupName: Option[String], partitionArgs: Filters)

private[transport] case class Subscription[OUT, IN](inputDecoder: Decoder[IN],
                                                     partitionArgsExtractor: FiltersExtractor[IN],
                                                     exceptionEncoder: Encoder[Throwable],
                                                     handler: (IN) => SubscriptionHandlerResult[OUT])

class NoTransportRouteException(message: String) extends RuntimeException(message)

