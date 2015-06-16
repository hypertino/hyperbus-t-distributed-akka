organization := "eu.inn"

name := "servicebus-t-distributed-akka"

libraryDependencies ++= Seq(
  "eu.inn" %% "servicebus" % version.value,
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "com.typesafe.akka" %% "akka-actor" % "2.3.11",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.11",
  "com.typesafe.akka" %% "akka-contrib" % "2.3.11",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "org.apache.directory.studio" % "org.apache.commons.io" % "2.4" % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)
