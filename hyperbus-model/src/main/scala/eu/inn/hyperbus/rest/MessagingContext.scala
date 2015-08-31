package eu.inn.hyperbus.rest

import eu.inn.servicebus.IdGenerator

trait MessagingContextFactory {
  def newContext(): MessagingContext
}

trait MessagingContext {
  def correlationId: String
  def messageId: String
}

object MessagingContext {
  def apply(withCorrelationId: String): MessagingContext = new MessagingContext {
    val messageId = IdGenerator.create()
    def correlationId = withCorrelationId
    override def toString = s"MessagingContext(messageId=$messageId,correlationId=$correlationId)"
  }
}

object MessagingContextFactory {
  implicit val newContextFactory = new MessagingContextFactory {
    def newContext() = new MessagingContext {
      val messageId = IdGenerator.create()
      def correlationId = messageId
      override def toString = s"NewMessagingContext(messageId=$messageId)"
    }
  }
}