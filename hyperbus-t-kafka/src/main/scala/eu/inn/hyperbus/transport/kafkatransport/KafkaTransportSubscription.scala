package eu.inn.hyperbus.transport.kafkatransport

import eu.inn.hyperbus.transport.api.Subscription

private[transport] case class KafkaTransportSubscription(key: TopicSubscriptionKey, underlyingId: Long) extends Subscription
