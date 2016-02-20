package eu.inn.hyperbus.transport.api

import eu.inn.hyperbus.transport.api.uri.Uri

case class TransportRoute[T](transport: T, uri: Uri, )
