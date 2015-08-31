package eu.inn.hyperbus.rest.standard

import eu.inn.hyperbus.rest.{Body, DynamicBody}
import eu.inn.hyperbus.serialization.ResponseHeader

object StandardResponseBody {
  def apply(responseHeader: ResponseHeader, responseBodyJson: com.fasterxml.jackson.core.JsonParser): Body = {
    if (responseHeader.status >= 400 && responseHeader.status <= 599)
      ErrorBody(responseHeader.contentType, responseBodyJson)
    else {
      responseHeader.status match {
        case Status.CREATED ⇒
          DynamicCreatedBody(responseHeader.contentType, responseBodyJson)
        case _ ⇒
          DynamicBody(responseHeader.contentType, responseBodyJson)
      }
    }
  }
}
