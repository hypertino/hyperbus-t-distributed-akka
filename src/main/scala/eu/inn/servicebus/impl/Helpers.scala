package eu.inn.servicebus.impl

import eu.inn.servicebus.serialization.Encoder
import eu.inn.servicebus.transport.HandlerResult

import scala.concurrent.Future
/*
case class ConvertHandler[OUT,IN](initHandler: (IN) => Future[OUT], encoder: Encoder[OUT]) {
  def handler(in: IN): HandlerResult[OUT] = {

  }
}

*/