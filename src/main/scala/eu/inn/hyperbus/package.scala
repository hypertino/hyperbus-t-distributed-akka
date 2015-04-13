package eu.inn

import eu.inn.binders.dynamic._
import scala.concurrent.{ExecutionContext, Future}

/*
todo:
  + annotations like // @WithContentType("application/vnd+identified-user.json"), @WithURI

 */
package object hyperbus {

  object Status {
    val OK = 200
    val CREATED = 201
  }

  object StandardLink {
    val SELF = "self"
    val LOCATION = "location"
  }

  object StandardMethods {
    val GET = "get"
    val POST = "post"
    val PUT = "put"
    val PATCH = "patch"
    val DELETE = "delete"
  }

  case class Link(href: String, templated: Option[Boolean] = None, typ: Option[String] = None)

  trait Body

  trait Links {
    def links: Map[String, Link]
  }

  trait ContentType {
    def contentType: String
  }
  
  abstract class Message[B <: Body](initBody:B){
    def body: B = initBody
  }

  abstract class Request[B <: Body](initBody:B) extends Message[B](initBody){
    def uri: String
    def method: String
  }

  abstract class Response[B <: Body](initBody:B) extends Message[B](initBody) {
    def status: Int
  }

  //success, redirect, etc

  // --------------- Responses ---------------

  class OK[B <: Body](initBody: B) extends Response[B](initBody) {
    override def status: Int = Status.OK
  }

  class CreatedResponseBody(initLocation: Link, otherLinks: Map[String, Link] = Map())
    extends Body with Links {
    val links = otherLinks + (StandardLink.LOCATION -> initLocation)
    def location = links(StandardLink.LOCATION)
  }

  class Created[B <: CreatedResponseBody](initBody: B) extends Response[B](initBody) {
    override def status: Int = Status.CREATED
  }

  // --------------- Request classes ---------------

  abstract class Get[B <: Body](initBody: B) extends Request[B](initBody) {
    override def method = StandardMethods.GET
  }

  abstract class Delete[B <: Body](initBody: B) extends Request[B](initBody) {
    override def method = StandardMethods.DELETE
  }

  abstract class Post[B <: Body](initBody: B) extends Request[B](initBody) {
    override def method = StandardMethods.POST
  }

  abstract class Put[B <: Body](initBody: B) extends Request[B](initBody) {
    override def method = StandardMethods.PUT
  }

  abstract class Patch[B <: Body](initBody: B) extends Request[B](initBody) {
    override def method = StandardMethods.PATCH
  }

  trait DefinedResponse[R <: Response[_]] {
    type responseType = R
  }

  // --------------- Dynamic ---------------

  class DynamicBody(content: DynamicValue) extends Body

  //class DynamicResponse(initBody: DynamicBody, val status: Int) extends Response[DynamicBody](initBody)

  /*class DynamicRequest(body: DynamicBody, val uriTemplate: String, val method: String) extends Request(body)
    with DefinedResponse[DynamicResponse]*/

  // --------------- Bus Example ---------------

  class Bus {
    val map = new scala.collection.mutable.HashMap[String, Any => Any]()

    def ask[IN,OUT](topic: String, t:IN):OUT = {
      map.get(topic) map { f =>
        f(t).asInstanceOf[OUT]
      } getOrElse {
        throw new RuntimeException(s"No route to topic $topic")
      }
    }

    def on[IN, OUT](topic: String, function: IN => OUT) = {
      val f: Any => Any = function.asInstanceOf[Any => Any]
      map += topic -> f
    }

    def off(topic: String) = map.remove(topic)
  }

  class HyperBus(bus: Bus) {

    def ??[RESP <: Response[_], REQ <: Request[_]] (r: REQ with DefinedResponse[RESP]): Future[RESP] = {
      val s = r.uri
      null
    }

    //def on
  }

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
      val bus = new Bus

      bus.on[String,Unit]("/test", (v: String) => {
        println(s"Received $v")
      })


      bus.ask[String,String] ("/test","ha")
/*
      import eu.inn.hyperbus.users._

      import ExecutionContext.Implicits.global

      hbus ?? SignUp(IdentifiedUser("123")) map { result =>
        println(result.body.location)
      }

      ///val g = new Get["Ha"  ]("1")
      */
    }
  }
}
