package eu.inn

import java.util.concurrent.atomic.AtomicLong

import eu.inn.binders.dynamic._
import eu.inn.servicebus.transport.{InprocTransport, ServerTransport, ClientTransport}
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.Random

/*
todo:
  + annotations like // @WithContentType("application/vnd+identified-user.json"), @WithURI
  + correlationId, sequenceId, replyTo
  + other headers?
  + Encoder/Decoder - String replace with Stream
  + remove unnecessary nulls

package object hyperbus {



  //class DynamicResponse(initBody: DynamicBody, val status: Int) extends Response[DynamicBody](initBody)

  class DynamicRequest(body: DynamicBody, val uriTemplate: String, val method: String) extends Request(body)
    with DefinedResponse[DynamicResponse]

  // --------------- Bus Example ---------------

  val hbus = new HyperBus(null)

  // --------------- Domain Example ---------------

  object users {

    case class IdentifiedUser(password: String) extends Body with ContentType {
      def contentType = "application/vnd+identified-user.json"
    }

    case class UserCreated(userId: String) extends CreatedResponseBody(
        Link("/users/{userId}", templated = Some(true))) {
    }

    case class SignUp(identifiedUser: IdentifiedUser) extends eu.inn.hyperbus.Post(identifiedUser)
      with DefinedResponse[Created[UserCreated]] {
      override def uri = "/users"
    }

    object apps {
      case class UserApps(userId: String, apps: Seq[String]) extends Body with ContentType {
        def contentType = "application/vnd+user-apps.json"
      }

      case class GetBody(userId: String) extends Body

      class Get(userId: String)
        extends eu.inn.hyperbus.Get(GetBody(userId))
        with DefinedResponse[OK[UserApps]] {
        override def uri = "/users/{userId}/apps"
      }
    }
  }

  object test {
    def x: Unit = {
      val inprocTransport = new InprocTransport
      val bus = new ServiceBus(inprocTransport,inprocTransport)

      //bus.on[String,Unit]("/test", (v: String) => {
        println(s"Received $v")
      })


      bus.ask[String,String] ("/test","ha")

      import eu.inn.hyperbus.users._

      import ExecutionContext.Implicits.global

      hbus ?? SignUp(IdentifiedUser("123")) map { result =>
        println(result.body.location)
      }

      ///val g = new Get["Ha"  ]("1")

    }
  }
}
*/