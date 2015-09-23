organization := "eu.inn"

name := "hyperbus-transport"

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

libraryDependencies ++= Seq(
  "eu.inn" %% "binders-core" % "0.10.69",
  "eu.inn" %% "binders-json" % "0.6.39",
  "eu.inn" %% "binders-typesafe-config" % "0.3.6",
  "com.typesafe" % "config" % "1.2.1",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
