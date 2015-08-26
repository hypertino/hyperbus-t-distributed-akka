package eu.inn.servicebus.transport.config

import eu.inn.servicebus.transport.{ClientTransport, ServerTransport, TransportRoute}

case class TransportConfiguration(clientRoutes: Seq[TransportRoute[ClientTransport]],
                                   serverRoutes: Seq[TransportRoute[ServerTransport]])
