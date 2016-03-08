package eu.inn.hyperbus.raml.utils

import eu.inn.binders.naming.{BaseConverter, DashCaseParser, IdentifierBuilder, SnakeUpperCaseBuilder}

class DashCaseToUpperSnakeCaseConverter extends BaseConverter(new DashCaseParser) {
  def createBuilder(): IdentifierBuilder = new SnakeUpperCaseBuilder()
}
