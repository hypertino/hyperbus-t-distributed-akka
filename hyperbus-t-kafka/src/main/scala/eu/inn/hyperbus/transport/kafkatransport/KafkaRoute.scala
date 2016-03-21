package eu.inn.hyperbus.transport.kafkatransport

import eu.inn.hyperbus.transport.api.matchers.RequestMatcher

case class KafkaRoute(requestMatcher: RequestMatcher,
                      kafkaTopic: String,
                      kafkaPartitionKeys: List[String])
