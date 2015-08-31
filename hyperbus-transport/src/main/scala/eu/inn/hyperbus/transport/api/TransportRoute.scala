package eu.inn.hyperbus.transport.api

case class TransportRoute[T](
                              transport: T,
                              urlArg: Filter,
                              valueFilters: Filters = Filters.empty
                              )
