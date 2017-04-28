crossScalaVersions := Seq("2.12.1", "2.11.8")

scalaVersion in Global := "2.12.1"

organization := "com.hypertino"

name := "hyperbus-t-distributed-akka"

version := "0.2-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.hypertino"   %% "hyperbus"        % "0.2-SNAPSHOT",
  "com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.17",
  "com.typesafe.akka" %% "akka-contrib" % "2.4.17",
  "org.scalamock"   %% "scalamock-scalatest-support" % "3.5.0" % "test",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.17" % "test",
  compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)
