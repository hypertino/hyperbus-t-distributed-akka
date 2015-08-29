package eu.inn.hyperbus.rest

import java.io.OutputStream

import eu.inn.binders.dynamic.Value
import eu.inn.hyperbus.serialization.impl.InnerHelpers
import eu.inn.servicebus.transport.{SpecificValue, Filters, Topic}


trait DynamicBody extends Body with Links {
  def content: Value

  lazy val links: Links.Map = content.__links[Option[Links.Map]] getOrElse Map.empty
}

object DynamicBody {
  def apply(content: Value, contentType: Option[String] = None): DynamicBody = DynamicBodyContainer(content, contentType)
  def unapply(dynamicBody: DynamicBody) = Some((dynamicBody.content, dynamicBody.contentType))
}

private [rest] case class DynamicBodyContainer(content: Value, contentType: Option[String] = None) extends DynamicBody

object DynamicMessage {
  def bodyEncoder(body: DynamicBody, outputStream: OutputStream): Unit = {
    import eu.inn.binders._
    eu.inn.binders.json.SerializerFactory.findFactory().withStreamGenerator(outputStream) { serializer=>
      serializer.bind[Value](body.content)
    }
  }
}

trait DynamicRequest extends Request[DynamicBody] {
  override def encode(outputStream: OutputStream): Unit = {
    InnerHelpers.encodeMessage[DynamicBody](this, DynamicMessage.bodyEncoder _, outputStream)
  }

   lazy val topic = Topic(url, Filters(UrlParser.extractParameters(url).map { arg ⇒
     arg → SpecificValue(
       body.content.asMap.get(arg).map(_.asString).getOrElse("") // todo: inner fields like abc.userId
     )
   }.toMap))
 }
