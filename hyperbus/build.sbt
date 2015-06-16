organization := "eu.inn"

name := "hyperbus"

libraryDependencies ++= Seq(
  "eu.inn" %% "hyperbus-inner" % version.value,
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
