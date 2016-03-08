package eu.inn.hyperbus.raml

import com.mulesoft.raml.webpack.holders.JSConsole
import com.mulesoft.raml1.java.parser.core.JavaNodeFactory
import eu.inn.hyperbus.raml.utils.JsToLogConsole
import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object Raml2HyperBus extends AutoPlugin {
  override def requires = JvmPlugin
  object autoImport {
    val ramlHyperBusSource = settingKey[File]("ramlHyperBusSource")
    val ramlHyperBusSourceIsResource = settingKey[Boolean]("ramlHyperBusSourceIsResource")
    val ramlHyperBusPackageName = settingKey[String]("ramlHyperBusPackageName")
    val ramlHyperBusContentTypePrefix = settingKey[Option[String]]("ramlHyperBusContentTypePrefix")
  }

  import autoImport._

  override val projectSettings =
    ramlHyperBusScopedSettings(Compile) ++ /*ramlHyperBusScopedSettings(Test) ++*/ r2hDefaultSettings

  protected def ramlHyperBusScopedSettings(conf: Configuration): Seq[Def.Setting[_]] = inConfig(conf)(Seq(
    sourceGenerators +=  Def.task {
      generateFromRaml(
        resourceDirectory.value,
        ramlHyperBusSource.value,
        ramlHyperBusSourceIsResource.value,
        sourceManaged.value,
        ramlHyperBusPackageName.value,
        ramlHyperBusContentTypePrefix.value
      )
    }.taskValue
  ))

  protected def r2hDefaultSettings: Seq[Def.Setting[_]] = Seq(
    ramlHyperBusContentTypePrefix := None,
    ramlHyperBusSourceIsResource := true
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
      val ramlApi = ramlFactory.createApi(apiFile.getAbsolutePath)
      val generator = new InterfaceGenerator(ramlApi, GeneratorOptions(packageName, contentPrefix))
      IO.write(outputFile, generator.generate())
    }
    Seq(outputFile)
  }
}
