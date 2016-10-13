organization := "eu.inn"

name := "hyperbus-transport"

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

libraryDependencies ++= Seq(
  "eu.inn" %% "binders-core" % "0.12.93",
  "eu.inn" %% "binders-json" % "0.12.47",
  "eu.inn" %% "binders-typesafe-config" % "0.12.15",
  "com.typesafe" % "config" % "1.2.1",
  "io.reactivex" %% "rxscala" % "0.26.3",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
