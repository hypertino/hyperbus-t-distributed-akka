import sbt.Keys._
import sbt._

object Build extends sbt.Build {
  lazy val paradiseVersionRoot = "2.1.0-M5"

  override lazy val settings =
    super.settings ++ Seq(
      organization := "eu.inn",
      version := "0.0.1",
      scalaVersion := "2.11.6",

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

      addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersionRoot cross CrossVersion.full)
    )

  lazy val `hyperbus-root` = project.in(file(".")) aggregate(servicebus, `hyperbus-inner`, hyperbus, `hyperbus-akka`, `servicebus-t-distributed-akka`, `hyperbus-cli`)
  lazy val servicebus = project.in(file("servicebus"))
  lazy val `hyperbus-inner` = project.in(file("hyperbus-inner")) dependsOn servicebus
  lazy val hyperbus = project.in(file("hyperbus")) dependsOn `hyperbus-inner`
  lazy val `hyperbus-akka` = project.in(file("hyperbus-akka")) dependsOn hyperbus
  lazy val `servicebus-t-distributed-akka` = project.in(file("servicebus-t-distributed-akka")) dependsOn servicebus
  lazy val `hyperbus-cli` = project.in(file("hyperbus-cli")) dependsOn (hyperbus, `servicebus-t-distributed-akka`)
}
