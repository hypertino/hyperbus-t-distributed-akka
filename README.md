TODO:
    + improve logging
    + reply on server if no method is handled (NoTransport alt/akka)
    + print routes on launch
    apply метод в request/response object принимающий содержимое body
    create configurable executors for service/bus & transports
    common transport code: logging, ?    
    try to serialize/deserialize with not-plain case convention
    kafka, inproc: add logging and subscriptionId like in akka
    replyTo ?
    + other headers? - extende RequestHeader & ResponseHeader
    + exception when duplicate subscription
    + test serialize/deserialize exceptions    
    low priority:
      + lost response response log details
    - distrib-akka Topic parition aware server and client ?    
    - custom http methods/verbs?
    + tests for different route/filters
        
    + criteria body (special content-type)?
    
    + akka serialization perf tests
    
    *-- remove url from request
    *-- typed topic (instead of extracting from body!) 
