TODO:
    apply метод в request/response object принимающий содержимое contentType
    create configurable executors for service/bus & transports
    common transport code
    todo eliminate helpers
    publish result with offset
    servicebus -> hyperbus
    rest -> model
    annotations -> model
    model -> ?
    encode to string, not to stream!
    try to serialize/deserialize with not-plain case convention
    
    - перенести encoder в message
    - перенести partitionextractor в topic в message
    - выделить exceptionEncoder
    - перенести body-decoder в companion
    
    kafka, inproc: add loggin and subscriptionId like in akka
    
macros:
    - генератор encoder/body-decoder перенести в request
    
    
other:
  // todo: Generic Errors and Responses
  // handle non-standrard status
