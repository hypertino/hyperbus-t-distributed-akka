package eu.inn.hyperbus.raml

import java.io.{File, IOException}

import com.mulesoft.raml.webpack.holders.JSConsole
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import eu.inn.hyperbus.raml.utils.JsToLogConsole
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
      val ramlFactory = new JavaNodeFactory()
      val existingConsole = ramlFactory.getBindings.get("console").asInstanceOf[JSConsole]
      ramlFactory.getBindings.put("console", new JsToLogConsole(existingConsole.engine))

      if (!apiFile.exists()) {
        throw new IOException(s"File ${apiFile.getAbsolutePath} doesn't exists")
      }

      val ramlApi = ramlFactory.createApi(apiFile.getAbsolutePath)
      val generator = new InterfaceGenerator(ramlApi, GeneratorOptions(packageName, contentPrefix))
      IO.write(outputFile, generator.generate())
    }
    Seq(outputFile)
  }
}
