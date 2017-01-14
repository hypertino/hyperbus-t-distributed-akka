package com.hypertino.hyperbus.raml.utils

import com.hypertino.inflector.naming.{BaseConverter, DashCaseParser, IdentifierBuilder, SnakeUpperCaseBuilder}

object DashCaseToUpperSnakeCaseConverter extends BaseConverter {
  val parser = new DashCaseParser
  def createBuilder(): IdentifierBuilder = new SnakeUpperCaseBuilder()
}
