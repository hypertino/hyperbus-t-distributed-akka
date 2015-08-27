package eu.inn.servicebus.transport

case class TransportRoute[T](
                              transport: T,
                              urlArg: Filter,
                              valueFilters: Filters = Filters.empty
                              )
