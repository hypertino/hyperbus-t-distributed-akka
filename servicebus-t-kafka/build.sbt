organization := "eu.inn"

name := "servicebus-t-kafka"

libraryDependencies ++= Seq(
  "eu.inn" %% "servicebus" % version.value,
  "org.apache.kafka" % "kafka-clients" % "0.8.2.1",
  "org.apache.kafka" %% "kafka" % "0.8.2.1" exclude("org.slf4j", "slf4j-log4j12"),
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.jsuereth" %% "scala-arm" % "1.4",
  "org.apache.directory.studio" % "org.apache.commons.io" % "2.4" % "test",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % "test"
)
