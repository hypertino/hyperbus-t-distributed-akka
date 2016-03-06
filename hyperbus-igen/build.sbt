organization := "eu.inn"

name := "hyperbus-igen"

libraryDependencies ++= Seq(
  "eu.inn" % "java-raml1-parser" % "0.0.31",
  "eu.inn" % "javascript-module-holders" % "0.0.31",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test"
)
