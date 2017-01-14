package com.hypertino.hyperbus.serialization

import com.hypertino.hyperbus.model.Header
import com.hypertino.hyperbus.transport.api.EntityWithHeaders
import com.hypertino.hyperbus.transport.api.uri.Uri

case class RequestHeader(uri: Uri, headers: Map[String, Seq[String]]) extends EntityWithHeaders {
  def messageId = header(Header.MESSAGE_ID)

  def correlationId = headerOption(Header.CORRELATION_ID).getOrElse(messageId)

  def contentType = headerOption(Header.CONTENT_TYPE)

  def method = header(Header.METHOD)
}
