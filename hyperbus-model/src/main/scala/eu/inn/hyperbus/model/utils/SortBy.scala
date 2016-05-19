package eu.inn.hyperbus.model.utils

import eu.inn.binders.value.Text
import eu.inn.hyperbus.model.{QueryBody, QueryBuilder}

case class SortBy(fieldName: String, descending: Boolean = false)
object Sort {
  val SORT_FIELD_NAME = "sort"

  implicit class Extractor(queryBody: QueryBody) {
    def sortBy: Option[Seq[SortBy]] = {
      queryBody.content.asMap.get(SORT_FIELD_NAME) match {
        case Some(Text(s)) ⇒ Some(parse(s))
        case Some(_) ⇒ Some(Seq.empty)
        case _ ⇒ None
      }
    }
  }

  implicit class Builder(queryBuilder: QueryBuilder) {
    def sortBy(sortSeq: Seq[SortBy]): QueryBuilder = {
      queryBuilder.add(SORT_FIELD_NAME, generate(sortSeq))
    }
  }

  def parse(value: String): Seq[SortBy] = {
    value.split(',').map { f ⇒
      if (f.startsWith("-")) {
        SortBy(f.substring(1), descending = true)
      }
      else {
        if (f.startsWith("+")) {
          SortBy(f.substring(1), descending = false)
        }
        else {
          SortBy(f, descending = false)
        }
      }
    }
  }

  def generate(seq: Seq[SortBy]): String = {
    seq.map { s ⇒
      if (s.descending) "-" + s.fieldName else s.fieldName
    } mkString ","
  }
}

