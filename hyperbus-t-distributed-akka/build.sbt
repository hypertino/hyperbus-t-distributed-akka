organization := "eu.inn"

name := "hyperbus-t-distributed-akka"

val akkaV = "2.4.1"

libraryDependencies ++= Seq(
  "eu.inn" %% "hyperbus-transport" % version.value,
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-contrib" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "org.apache.directory.studio" % "org.apache.commons.io" % "2.4" % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)
