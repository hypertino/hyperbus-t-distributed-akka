organization := "eu.inn"

name := "hyperbus-akka"

libraryDependencies ++= Seq(
  "eu.inn" %% "hyperbus" % version.value,
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.1" % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
