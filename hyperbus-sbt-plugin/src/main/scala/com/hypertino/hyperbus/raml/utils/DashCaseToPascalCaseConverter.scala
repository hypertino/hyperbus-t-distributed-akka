package com.hypertino.hyperbus.raml.utils

import com.hypertino.inflector.naming.{BaseConverter, DashCaseParser, IdentifierBuilder, PascalCaseBuilder}

object DashCaseToPascalCaseConverter extends BaseConverter {
  val parser = new DashCaseParser
  def createBuilder(): IdentifierBuilder = new PascalCaseBuilder()
}
