organization := "eu.inn"

name := "hyperbus"

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

libraryDependencies ++= Seq(
  "eu.inn" %% "binders-core" % "0.5.12",
  "eu.inn" %% "binders-json" % "0.5.12",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.14" % "test",
  // todo: move akka to separate library
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test"
)

val paradiseVersion = "2.1.0-M5"

addCompilerPlugin("org.scalamacros" % "paradise" % paradiseVersion cross CrossVersion.full)
