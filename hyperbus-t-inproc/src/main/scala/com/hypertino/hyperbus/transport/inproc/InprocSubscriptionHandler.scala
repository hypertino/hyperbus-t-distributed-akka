package com.hypertino.hyperbus.transport.inproc

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import com.hypertino.hyperbus.model.{Body, Request}
import com.hypertino.hyperbus.serialization._
import com.hypertino.hyperbus.transport.api._
import org.slf4j.LoggerFactory
import rx.lang.scala.Observer

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

private[transport] case class InprocSubscriptionHandler[REQ <: Request[Body]]
  (
    inputDeserializer: RequestDeserializer[REQ],
    handler: Either[REQ => Future[TransportResponse], Observer[REQ]]
  ) {

  def handleCommandOrEvent(serialize: Boolean,
                           subKey: SubKey,
                           message: TransportRequest,
                           outputDeserializer: Deserializer[TransportResponse],
                           isPublish: Boolean,
                           resultPromise: Promise[TransportResponse])
                          (implicit executionContext: ExecutionContext): Boolean = {
    var commandHandlerFound = false
    var eventHandlerFound = false

    if (subKey.groupName.isEmpty) {
      // default subscription (groupName="") returns reply

      tryX("onCommand` deserialization failed",
        reserializeRequest(serialize, message, inputDeserializer)
      ) foreach { messageForSubscriber : REQ ⇒

        val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

        if (matched) {
          commandHandlerFound = true
          requestHandler(messageForSubscriber) map { response ⇒
            if (!isPublish) {
              val finalResponse = if (serialize) {
                reserializeResponse(serialize, response, outputDeserializer)
              } else {
                response
              }
              resultPromise.success(finalResponse)
            }
          } recover {
            case NonFatal(e) ⇒
              InprocSubscriptionHandler.log.error("`onCommand` handler failed with", e)
          }
        }
        else {
          InprocSubscriptionHandler.log.error(s"Message ($messageForSubscriber) is not matched after serialize and deserialize")
        }
      }
    } else {
      tryX("onEvent` deserialization failed",
        reserializeRequest(serialize, message, inputDeserializer)
      ) foreach { messageForSubscriber: REQ ⇒

        val matched = !serialize || subKey.requestMatcher.matchMessage(messageForSubscriber)

        if (matched) {
          eventHandlerFound = true
          eventHandler.onNext(messageForSubscriber)
        }
        else {
          InprocSubscriptionHandler.log.error(s"Message ($messageForSubscriber) is not matched after serialize and deserialize")
        }
      }
    }
    commandHandlerFound || eventHandlerFound
  }

  private def reserializeRequest(serialize: Boolean, message: TransportMessage, deserializer: RequestDeserializer[REQ]): REQ = {
    if (serialize) {
      val ba = new ByteArrayOutputStream()
      message.serialize(ba)
      val bi = new ByteArrayInputStream(ba.toByteArray)
      MessageDeserializer.deserializeRequestWith(bi)(deserializer)
    }
    else {
      message.asInstanceOf[REQ]
    }
  }

  private def reserializeResponse[OUT <: TransportResponse](serialize: Boolean, message: TransportMessage, deserializer: Deserializer[OUT]): OUT = {
    if (serialize) {
      val ba = new ByteArrayOutputStream()
      message.serialize(ba)
      val bi = new ByteArrayInputStream(ba.toByteArray)
      deserializer(bi)
    }
    else {
      message.asInstanceOf[OUT]
    }
  }

  private def tryX[T](failMsg: String, code: ⇒ T): Option[T] = {
    try {
      Some(code)
    }
    catch {
      case NonFatal(e) ⇒
        InprocSubscriptionHandler.log.error(failMsg, e)
        None
    }
  }

  private def requestHandler = handler.left.get

  private def eventHandler = handler.right.get
}

object InprocSubscriptionHandler {
  protected val log = LoggerFactory.getLogger(this.getClass)
}