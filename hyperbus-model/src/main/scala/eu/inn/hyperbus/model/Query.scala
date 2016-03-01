package eu.inn.hyperbus.model

import java.net.{URLDecoder, URLEncoder}

import eu.inn.binders.dynamic._
import scala.collection.mutable

trait Query extends DynamicBody {
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

  def pageFrom: Option[Value] = content.asMap.get(DefQuery.PAGE_FROM)
  def pageSize: Option[Value] = content.asMap.get(DefQuery.PAGE_SIZE)
  def filter: Obj = Obj(content.asMap.filterNot(_._1.contains(".")))
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

  def fromQueryString(queryString: String) = new QueryBuilder() addQueryString queryString result()

  def unapply(query: Query) = Some((query.contentType, query.content))
}

private[model] case class QueryContainer(contentType: Option[String], content: Value) extends Query

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

  def pageFrom(value: Value): QueryBuilder = {
    args += DefQuery.PAGE_FROM → value
    this
  }

  def pageSize(value: Value): QueryBuilder = {
    args += DefQuery.PAGE_SIZE → value
    this
  }

  def sortBy(field: String): QueryBuilder = {
    args += DefQuery.SORT_BY → Text(field)
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

  def result(): Query = QueryContainer(None, Obj(args.toMap))
}

object DefQuery {
  val PAGE_FROM       = "page.from"
  val PAGE_SIZE       = "page.size"
  val SORT_BY         = "sort.by"
}