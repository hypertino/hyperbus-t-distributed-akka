package eu.inn.hyperbus.util

import eu.inn.hyperbus.transport.api.{TransportResponse, TransportRequest, TransportMessage}
import org.slf4j.Logger

trait TransportUtils {
  protected def logMessages: Boolean
  protected def log: Logger
  import LogUtils._

  def logAskRequest(message: TransportRequest) = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → message.messageId,"correlationId" → message.correlationId), s"hyperBus <~ $message")
    }
  }

  def logAskResponse(message: TransportResponse) = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → message.messageId,"correlationId" → message.correlationId), s"hyperBus ~(R)~> $message")
    }
  }

  def logPublishMessage(message: TransportRequest) = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → message.messageId,"correlationId" → message.correlationId), s"hyperBus <| $message")
    }
  }

  def logCommandRequest(message: TransportRequest, subscriptionId: String) = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → message.messageId,"correlationId" → message.correlationId, "subscriptionId" → subscriptionId),
        s"hyperBus ~> $message")
    }
  }

  def logCommandResponse(message: TransportResponse, subscriptionId: String) = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → message.messageId,"correlationId" → message.correlationId, "subscriptionId" → subscriptionId),
        s"hyperBus <~(R)~ $message")
    }
  }

  def logEventMessage(message: TransportRequest, subscriptionId: String) = {
    if (logMessages && log.isTraceEnabled) {
      log.trace(Map("messageId" → message.messageId,"correlationId" → message.correlationId, "subscriptionId" → subscriptionId),
        s"hyperBus |> $message")
    }
  }
}
