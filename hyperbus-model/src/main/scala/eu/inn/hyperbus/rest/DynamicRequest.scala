package eu.inn.hyperbus.rest

import java.io.OutputStream

import eu.inn.binders.dynamic.Value
import eu.inn.servicebus.transport.{SpecificValue, Filters, Topic}


trait DynamicBody extends Body with Links {
  def content: Value
  val links: Links.Map = ???
  //lazy val links: Links.Map = content.__links[Option[Links.Map]].getOrElse(Map.empty) // todo: wtf with this?
}

object DynamicBody {
  def apply(content: Value, contentType: Option[String] = None): DynamicBody = DynamicBodyContainer(content, contentType)
  def apply(jsonParser : com.fasterxml.jackson.core.JsonParser, contentType: Option[String] = None): DynamicBody = {
    import eu.inn.binders._
    eu.inn.binders.json.SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(deserializer.unbind[Value], contentType)
    }
  }
  def apply(jsonParser : com.fasterxml.jackson.core.JsonParser): DynamicBody = {
    apply(jsonParser, None)
  }
  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.content, dynamicBody.contentType))
}

private [rest] case class DynamicBodyContainer(content: Value, contentType: Option[String] = None) extends DynamicBody {
  def encode(outputStream: OutputStream): Unit = {
    import eu.inn.binders._
    eu.inn.binders.json.SerializerFactory.findFactory().withStreamGenerator(outputStream) { serializer=>
      serializer.bind[Value](content)
    }
  }
}

trait DynamicRequest extends Request[DynamicBody] {
  lazy val topic = Topic(url, Filters(UrlParser.extractParameters(url).map { arg ⇒
    arg → SpecificValue(
      body.content.asMap.get(arg).map(_.asString).getOrElse("") // todo: inner fields like abc.userId
    )
  }.toMap))
}
