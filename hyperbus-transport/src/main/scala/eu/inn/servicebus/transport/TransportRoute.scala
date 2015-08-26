package eu.inn.servicebus.transport

case class TransportRoute[T](
                              transport: T,
                              urlArg: PartitionArg,
                              partitionArgs: PartitionArgs = PartitionArgs(Map.empty)
                              )
