package eu.inn.hyperbus.model

import eu.inn.binders.dynamic.{Null, Value}
import scala.collection.mutable

trait Query extends DynamicBody {

}

object Query {
  def apply(contentType: Option[String], content: Value): Query = QueryContainer(contentType, content)

  def apply(): QueryContainer = Query(Null)

  def apply(content: Value): QueryContainer = QueryContainer(None, content)

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): Query = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(contentType, deserializer.unbind[Value])
    }
  }

  def unapply(query: Query) = Some((query.contentType, query.content))
}

private[model] case class QueryContainer(contentType: Option[String], content: Value) extends Query

class QueryBuilder(private [this] val args: mutable.Map[String, Value]) {
  def this() = this(mutable.Map[String, Value]())

  def filter(kv: (String, Value)): QueryBuilder = {
    args += kv
    this
  }

  def result(): Query = ???
}