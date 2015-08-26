package eu.inn.servicebus.transport

case class ServiceBusConfiguration(clientRoutes: Seq[TransportRoute[ClientTransport]],
                                   serverRoutes: Seq[TransportRoute[ServerTransport]])
