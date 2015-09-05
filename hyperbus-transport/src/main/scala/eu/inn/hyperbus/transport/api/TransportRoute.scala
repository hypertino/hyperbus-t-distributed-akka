package eu.inn.hyperbus.transport.api

case class TransportRoute[T](transport: T, topic: Topic)
