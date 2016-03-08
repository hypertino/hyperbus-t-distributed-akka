package eu.inn.hyperbus.raml.utils

import eu.inn.binders.naming.{BaseConverter, DashCaseParser, IdentifierBuilder, PascalCaseBuilder}

class DashCaseToPascalCaseConverter extends BaseConverter(new DashCaseParser) {
  def createBuilder(): IdentifierBuilder = new PascalCaseBuilder()
}
