package com.hypertino.hyperbus.impl

import com.hypertino.hyperbus.model.{Body, Header, Response}
import com.hypertino.hyperbus.serialization.{ResponseBodyDeserializer, ResponseHeader}
import com.hypertino.hyperbus.transport.api.matchers.{Any, Specific, RequestMatcher}
import com.hypertino.hyperbus.transport.api.uri._

trait MacroApi {
  def responseDeserializer(responseHeader: ResponseHeader,
                           responseBodyJson: com.fasterxml.jackson.core.JsonParser,
                           bodyDeserializer: PartialFunction[ResponseHeader, ResponseBodyDeserializer]): Response[Body]

  private def uriWithAnyValue(uriPattern: String): Uri = Uri(
    Specific(uriPattern),
    UriParser.extractParameters(uriPattern).map(_ → Any).toMap
  )

  def requestMatcher(uriPattern: String, method: String, contentType: Option[String]): RequestMatcher = {
    RequestMatcher(Some(uriWithAnyValue(uriPattern)), Map(Header.METHOD → Specific(method)) ++
      contentType.map(c ⇒ Header.CONTENT_TYPE → Specific(c))
    )
  }
}
