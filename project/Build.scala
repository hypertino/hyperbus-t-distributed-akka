import sbt.Keys._
import sbt._

object Build extends sbt.Build {
  lazy val paradiseVersionRoot = "2.1.0-M5"

  val projectMajorVersion = settingKey[String]("Defines the major version number")
  val projectBuildNumber = settingKey[String]("Defines the build number")

  override lazy val settings =
    super.settings ++ Seq(
      organization := "eu.inn",
      scalaVersion := "2.11.7",
      projectMajorVersion := "0.1",
      projectBuildNumber := "SNAPSHOT",
      version := projectMajorVersion.value + "." + projectBuildNumber.value,

      scalacOptions ++= Seq(
        "-feature",
        "-deprecation",
        "-unchecked",
        "-optimise",
        "-target:jvm-1.7",
        "-encoding", "UTF-8"
      ),

      javacOptions ++= Seq(
        "-source", "1.7",
        "-target", "1.7",
        "-encoding", "UTF-8",
        "-Xlint:unchecked",
        "-Xlint:deprecation"
      ),

      publishTo := Some("Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local"),

      credentials += Credentials(Path.userHome / ".ivy2" / ".innova_credentials"),

      addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersionRoot cross CrossVersion.full)
    )

  lazy val `hyperbus-root` = project.in(file(".")) aggregate(
    `hyperbus-transport`,
    `hyperbus-annotations`,
    `hyperbus-model`,
    hyperbus,
    `hyperbus-akka`,
    `hyperbus-t-inproc`,
    `hyperbus-t-distributed-akka`,
    `hyperbus-t-kafka`,
    `hyperbus-cli`
  )
  lazy val `hyperbus-transport` = project.in(file("hyperbus-transport"))
  lazy val `hyperbus-annotations` = project.in(file("hyperbus-annotations")) dependsOn `hyperbus-transport`
  lazy val `hyperbus-model` = project.in(file("hyperbus-model")) dependsOn (`hyperbus-transport`, `hyperbus-annotations`)
  lazy val hyperbus = project.in(file("hyperbus")) dependsOn `hyperbus-model`
  lazy val `hyperbus-akka` = project.in(file("hyperbus-akka")) dependsOn hyperbus
  lazy val `hyperbus-t-inproc` = project.in(file("hyperbus-t-inproc")) dependsOn `hyperbus-transport`
  lazy val `hyperbus-t-distributed-akka` = project.in(file("hyperbus-t-distributed-akka")) dependsOn `hyperbus-transport`
  lazy val `hyperbus-t-kafka` = project.in(file("hyperbus-t-kafka")) dependsOn `hyperbus-transport`
  lazy val `hyperbus-cli` = project.in(file("hyperbus-cli")) dependsOn (hyperbus, `hyperbus-t-distributed-akka`)
}
