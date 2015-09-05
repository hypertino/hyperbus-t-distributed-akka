package eu.inn.hyperbus.transport.api

// todo: do we really need this?
case class TransportConfiguration(clientRoutes: Seq[TransportRoute[ClientTransport]],
                                  serverRoutes: Seq[TransportRoute[ServerTransport]])
