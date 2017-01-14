package com.hypertino.hyperbus.raml

import java.io.{File, FileNotFoundException}

import org.raml.v2.api.RamlModelBuilder
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object Raml2Hyperbus extends AutoPlugin {
  override def requires = JvmPlugin
  object autoImport {
    val ramlHyperbusSource = settingKey[File]("ramlHyperbusSource")
    val ramlHyperbusSourceIsResource = settingKey[Boolean]("ramlHyperbusSourceIsResource")
    val ramlHyperbusPackageName = settingKey[String]("ramlHyperbusPackageName")
    val ramlHyperbusContentTypePrefix = settingKey[Option[String]]("ramlHyperbusContentTypePrefix")
  }

  import autoImport._

  override val projectSettings =
    ramlHyperbusScopedSettings(Compile) ++ /*ramlHyperbusScopedSettings(Test) ++*/ r2hDefaultSettings

  protected def ramlHyperbusScopedSettings(conf: Configuration): Seq[Def.Setting[_]] = inConfig(conf)(Seq(
    sourceGenerators +=  Def.task {
      generateFromRaml(
        resourceDirectory.value,
        ramlHyperbusSource.value,
        ramlHyperbusSourceIsResource.value,
        sourceManaged.value,
        ramlHyperbusPackageName.value,
        ramlHyperbusContentTypePrefix.value
      )
    }.taskValue
  ))

  protected def r2hDefaultSettings: Seq[Def.Setting[_]] = Seq(
    ramlHyperbusContentTypePrefix := None,
    ramlHyperbusSourceIsResource := true
  )

  protected def generateFromRaml(resourceDirectory: File,
                                 source: File,
                                 sourceIsResource: Boolean,
                                 base: File, packageName: String,
                                 contentPrefix: Option[String]): Seq[File] = {

    val outputFile = base / "r2h" / (packageName.split('.').mkString("/") + "/" + source.getName + ".scala")
    val apiFile = if (sourceIsResource) {
      resourceDirectory / source.getPath
    } else {
      source
    }
    if (!outputFile.canRead || outputFile.lastModified() < apiFile.lastModified()) {
      if (!apiFile.exists()) {
        throw new FileNotFoundException(s"File ${apiFile.getAbsolutePath} doesn't exists")
      }

      val ramlApi = new RamlModelBuilder().buildApi(apiFile).getApiV10
      val generator = new InterfaceGenerator(ramlApi, GeneratorOptions(packageName, contentPrefix))
      IO.write(outputFile, generator.generate())
    }
    Seq(outputFile)
  }
}
