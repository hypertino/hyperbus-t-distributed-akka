package eu.inn.hyperbus.model

import java.net.{URLDecoder, URLEncoder}

import eu.inn.binders.value._
import scala.collection.mutable
import scala.util.control.NonFatal



trait QueryBody extends DynamicBody {
  def toQueryString(encoding: String = "UTF-8"): String = {
    content.asMap.flatMap { case (key, value) ⇒
      value match {
        case Lst(list) ⇒ list.map { el ⇒
          URLEncoder.encode(key, encoding) + "=" + URLEncoder.encode(el.asString, encoding)
        }
        case other ⇒ Seq(
          URLEncoder.encode(key, encoding) + "=" + URLEncoder.encode(value.asString, encoding)
        )
      }
    } mkString "&"
  }

  def filter: Obj = Obj(content.asMap.filterNot(_._1.contains(".")))
}

object QueryBody {
  def apply(contentType: Option[String], content: Value): QueryBody = QueryBodyContainer(contentType, content)

  def apply(): QueryBodyContainer = QueryBody(Null)

  def apply(content: Value): QueryBodyContainer = QueryBodyContainer(None, content)

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): QueryBody = {
    import eu.inn.binders.json._
    SerializerFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      apply(contentType, deserializer.unbind[Value])
    }
  }

  def fromQueryString(queryString: String) = new QueryBuilder() addQueryString queryString result()

  def fromQueryString(query: Map[String, String]) = {
    val builder = new QueryBuilder()
    query.foreach { kv ⇒
      if (kv._2.isEmpty) builder.add((kv._1, Null))
      else builder.add((kv._1, Text(kv._2)))
    }
    builder.result()
  }

  def unapply(query: QueryBody) = Some((query.contentType, query.content))
}

private[model] case class QueryBodyContainer(contentType: Option[String], content: Value) extends QueryBody

class QueryBuilder(private [this] val args: mutable.Map[String, Value]) {
  def this() = this(mutable.Map[String, Value]())

  def add(kv: (String, Value)): QueryBuilder = {
    args.get(kv._1) match {
      case Some(Lst(existing)) ⇒
        args += kv._1 → Lst(existing :+ kv._2)
      case Some(existing) ⇒
        args += kv._1 → Lst(Seq(existing, kv._2))
      case None ⇒
        args += kv
    }
    this
  }

  def addQueryString(queryString: String, encoding: String = "UTF-8"): QueryBuilder = {
    if (!queryString.isEmpty) {
      val q = if (queryString.charAt(0) == '?')
        queryString.substring(1)
      else
        queryString

      q.split('&').foreach { qs ⇒
        val i = qs.indexOf('=')
        if (i > 0) {
          val key = URLDecoder.decode(qs.substring(0, i), encoding)
          if ((i+1) == qs.length) {
            add(key → Null)
          }
          else {
            add(key → Text(URLDecoder.decode(qs.substring(i+1), encoding)))
          }
        }
        else {
          add(qs → Null)
        }
      }
    }
    this
  }

  def result(): QueryBody = QueryBodyContainer(None, Obj(args.toMap))
}
