import sbt.Keys._
import sbt._

object Build extends sbt.Build {
  lazy val paradiseVersionRoot = "2.1.0-M5"

  val projectMajorVersion = settingKey[String]("Defines the major version number")
  val projectBuildNumber = settingKey[String]("Defines the build number")

  override lazy val settings =
    super.settings ++ Seq(
      organization := "eu.inn",
      scalaVersion := "2.11.6",
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
    servicebus,
    `hyperbus-annotations`,
    `hyperbus-inner`,
    hyperbus,
    `hyperbus-akka`,
    `servicebus-t-distributed-akka`,
    `servicebus-t-kafka`,
    `hyperbus-cli`
  )
  lazy val servicebus = project.in(file("servicebus"))
  lazy val `hyperbus-annotations` = project.in(file("hyperbus-annotations")) dependsOn servicebus
  lazy val `hyperbus-inner` = project.in(file("hyperbus-inner")) dependsOn (servicebus, `hyperbus-annotations`)
  lazy val hyperbus = project.in(file("hyperbus")) dependsOn `hyperbus-inner`
  lazy val `hyperbus-akka` = project.in(file("hyperbus-akka")) dependsOn hyperbus
  lazy val `servicebus-t-distributed-akka` = project.in(file("servicebus-t-distributed-akka")) dependsOn servicebus
  lazy val `servicebus-t-kafka` = project.in(file("servicebus-t-kafka")) dependsOn servicebus
  lazy val `hyperbus-cli` = project.in(file("hyperbus-cli")) dependsOn (hyperbus, `servicebus-t-distributed-akka`)
}
