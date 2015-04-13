package eu.inn.forgame.statusmonitor

import eu.inn.util.ConfigComponent
import eu.inn.util.akka.ActorSystemComponent
import eu.inn.util.http.clients.NingHttpClientComponent
import eu.inn.util.metrics.StatsComponent
import eu.inn.util.servicebus.{ServiceBusComponent, ServiceConsumerComponent}

trait ComponentRegistry
  extends ActorSystemComponent
    with ConfigComponent
    with ServiceBusComponent
    with NingHttpClientComponent
    with StatsComponent
    with ServiceConsumerComponent
    with StatusMonitorComponent
