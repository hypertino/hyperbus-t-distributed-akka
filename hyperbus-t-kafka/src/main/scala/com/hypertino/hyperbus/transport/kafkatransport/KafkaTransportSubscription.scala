package com.hypertino.hyperbus.transport.kafkatransport

import com.hypertino.hyperbus.transport.api.Subscription

private[transport] case class KafkaTransportSubscription(key: TopicSubscriptionKey, underlyingId: Long) extends Subscription
