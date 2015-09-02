TODO:
    apply метод в request/response object принимающий содержимое body
    create configurable executors for service/bus & transports
    common transport code    
    encode to string, not to stream!
    try to serialize/deserialize with not-plain case convention
    
    - выделить exceptionEncoder
    
    kafka, inproc: add logging and subscriptionId like in akka

    replyTo ?
    + other headers? - extende RequestHeader & ResponseHeader
    + exception when duplicate subscription
    + test serialize/deserialize exceptions
    
    low priority:
      + lostResponse response log details

TODO:
    - Topic parition aware server and client    
other:
  // todo: Generic Errors and Responses
  // handle non-standrard status
