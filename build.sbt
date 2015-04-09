organization := "eu.inn"

name := "forgame-status-monitor"

version := "1.0"

scalaVersion := "2.11.5"

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-optimise",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

javacOptions ++= Seq(
  "-source", "1.7",
  "-target", "1.7",
  "-encoding", "UTF-8",
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

publishTo := Some("Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local")

credentials += Credentials(Path.userHome / ".ivy2" / ".innova_credentials")

resolvers += "Innova libs repo" at "http://repproxy.srv.inn.ru/artifactory/libs-release-local"

libraryDependencies ++= Seq(
  "eu.inn" %% "sbus-core" % "[0.1.+]",
  "eu.inn" %% "forgame-datamodel" % "[0.1.+]",
  "eu.inn" %% "fluentd-scala" % "0.1.10",
  "eu.inn" %% "util-test" % "[0.1.+]" % "test",
  "eu.inn" %% "util-is-client" % "[0.1.+]",
  "eu.inn" %% "util-jdbc" % "[0.1.+]",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test"
)
