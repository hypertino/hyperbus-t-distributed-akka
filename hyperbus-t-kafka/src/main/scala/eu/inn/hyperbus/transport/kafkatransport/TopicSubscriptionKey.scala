package eu.inn.hyperbus.transport.kafkatransport

private[transport] case class TopicSubscriptionKey(kafkaTopic: String, kafkaPartitionKeys: List[String], groupName: String)
