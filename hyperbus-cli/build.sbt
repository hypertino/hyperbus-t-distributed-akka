organization := "eu.inn"

name := "hyperbus-cli"

val akkaV = "2.4.1"

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)

libraryDependencies ++= Seq(
  "jline" % "jline" % "2.12.1",
  "eu.inn" %% "binders-core" % "0.11.75",
  "eu.inn" %% "service-control" % "0.1.16",
  "eu.inn" %% "service-config" % "0.1.3",
  "eu.inn" %% "hyperbus" % version.value,
  "eu.inn" %% "hyperbus-t-distributed-akka" % version.value,
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test"
)
