package eu.inn.hyperbus.raml

import sbt._
import Keys._
import sbt.plugins.JvmPlugin

object GeneratorPlugin extends AutoPlugin {
  override def requires = JvmPlugin
  object autoImport {
    val igenPackageName = settingKey[String]("igenPackageName")
    val igenContentTypePrefix = settingKey[String]("igenContentTypePrefix")


    lazy val igenBaseSettingsCompile: Seq[Def.Setting[_]] = Seq(
      sourceGenerators in Compile +=  Def.task {
        val file = (sourceManaged in Compile).value / "demo" / "Test.scala"
        IO.write(file, """object Test extends App { println("Hi") }""")
        Seq(file)
      }.taskValue
    )

    lazy val igenBaseSettingsTest: Seq[Def.Setting[_]] = Seq(
      sourceGenerators in Test +=  Def.task {
        val file = (sourceManaged in Test).value / "demo-test" / "Test.scala"
        IO.write(file, """object Test extends App { println("Hi") }""")
        Seq(file)
      }.taskValue
    )
  }

  import autoImport._

  override val projectSettings =
    inConfig(Compile)(igenBaseSettingsCompile) ++
    inConfig(Test)(igenBaseSettingsTest)
}

object Generate {
  def apply(base: File): Seq[File] = Seq()
}