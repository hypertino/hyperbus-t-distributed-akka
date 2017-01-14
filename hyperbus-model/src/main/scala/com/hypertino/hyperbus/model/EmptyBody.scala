package com.hypertino.hyperbus.model

import com.hypertino.binders.json.JsonBindersFactory
import com.hypertino.binders.value._

trait EmptyBody extends DynamicBody

case object EmptyBody extends EmptyBody {
  def contentType: Option[String] = None

  def content = Null

  def apply(contentType: Option[String], jsonParser: com.fasterxml.jackson.core.JsonParser): EmptyBody = {
    import com.hypertino.binders.json.JsonBinders._
    JsonBindersFactory.findFactory().withJsonParser(jsonParser) { deserializer =>
      deserializer.unbind[Value]
    }
    EmptyBody
  }
}