organization := "eu.inn"

name := "hyperbus-t-inproc"

libraryDependencies ++= Seq(
  "eu.inn" %% "hyperbus-transport" % version.value,
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)
