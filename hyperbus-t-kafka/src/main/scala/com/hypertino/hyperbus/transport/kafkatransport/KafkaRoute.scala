package com.hypertino.hyperbus.transport.kafkatransport

import com.hypertino.hyperbus.transport.api.matchers.RequestMatcher

case class KafkaRoute(requestMatcher: RequestMatcher,
                      kafkaTopic: String,
                      kafkaPartitionKeys: List[String])
