package eu.inn.hyperbus.utils

import java.util.UUID

object ErrorUtils {
  def createErrorId: String = UUID.randomUUID().toString
}
