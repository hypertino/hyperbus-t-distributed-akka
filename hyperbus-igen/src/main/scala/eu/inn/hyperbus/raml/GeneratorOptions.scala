package eu.inn.hyperbus.raml

case class GeneratorOptions(packageName: String,
                            contentTypePrefix: Option[String] = None,
                            generatorInformation: Boolean = true,
                            defaultImports:Boolean = true,
                            customImports: Option[String] = None
                           )
