organization := "eu.inn"

name := "hyperbus-macro"

version := "0.0.1"

scalaVersion := "2.11.6"

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-optimise",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

javacOptions ++= Seq(
  "-source", "1.7",
  "-target", "1.7",
  "-encoding", "UTF-8",
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

val paradiseVersion = "2.1.0-M5"

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
