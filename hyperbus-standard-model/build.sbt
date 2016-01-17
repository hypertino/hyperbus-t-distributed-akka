organization := "eu.inn"

name := "hyperbus-standard-model"

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

libraryDependencies ++= Seq(
  "eu.inn" %% "hyperbus-model" % version.value,
  "eu.inn" %% "binders-core" % "0.10.70",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
