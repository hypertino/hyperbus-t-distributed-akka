import sbt._
import Keys._

object Build extends sbt.Build {

  override lazy val settings =
    super.settings ++ Seq(
      organization := "eu.inn",
      version      := "1.0-SNAPSHOT",
      scalaVersion := "2.11.6",

      scalacOptions ++= Seq(
        "-language:postfixOps",
        "-language:implicitConversions",
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
      )
    )

    //lazy val hyperbusMacro = project.in(file("hyperbus-macro"))
    lazy val hyperbus    = project.in(file("hyperbus"))
    lazy val forgameStatusMonitor = project.in(file("status-monitor")) dependsOn hyperbus
    lazy val rootX = project.in(file(".")) aggregate (hyperbus, forgameStatusMonitor)
}
