package eu.inn.hyperbus.transport.api

import java.io.OutputStream

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

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

case class Topic(urlFilter: Filter, valueFilters: Filters = Filters.empty) { // todo: add topic matcher and used it!
  override def toString = s"Topic($urlFilter$valueFiltersFormat)"
  private def valueFiltersFormat = if(valueFilters.filterMap.isEmpty) "" else
    valueFilters.filterMap.mkString("#",",","")
}

object Topic {
  def apply(url: String): Topic = Topic(SpecificValue(url), Filters.empty)
  def apply(url: String, valueFilters: Filters): Topic = Topic(SpecificValue(url), valueFilters)
}

trait TransportMessage {
  def messageId: String
  def correlationId: String
  def encode(output: OutputStream)
}

trait TransportRequest extends TransportMessage {
  def topic: Topic
}

trait TransportResponse extends TransportMessage

trait ClientTransport {
  def ask[OUT <: TransportResponse](message: TransportRequest, outputDecoder: Decoder[OUT]): Future[OUT]
  def publish(message: TransportRequest): Future[Unit]
  def shutdown(duration: FiniteDuration): Future[Boolean]
}

trait ServerTransport {
  def process[IN <: TransportRequest](topicFilter: Topic, inputDecoder: Decoder[IN], exceptionEncoder: Encoder[Throwable])
                 (handler: (IN) => Future[TransportResponse]): String

  def subscribe[IN <: TransportRequest](topicFilter: Topic, groupName: String, inputDecoder: Decoder[IN])
                   (handler: (IN) => Future[Unit]): String // todo: Unit -> some useful response?

  def off(subscriptionId: String)
  def shutdown(duration: FiniteDuration): Future[Boolean]
}

class NoTransportRouteException(message: String) extends RuntimeException(message)

