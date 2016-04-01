organization := "eu.inn"

name := "hyperbus-sbt-plugin"

sbtPlugin := true

scalaVersion := "2.10.6"

scalacOptions := Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-optimise",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

libraryDependencies ++= Seq(
  "eu.inn" %% "binders-core"  % "0.12.85",
  "eu.inn" % "java-raml1-parser" % "0.0.31",
  "eu.inn" % "javascript-module-holders" % "0.0.31",
  "org.jibx" % "jibx-tools" % "1.2.6",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "org.mockito" % "mockito-all" % "1.10.19" % "test",
  "org.bitbucket.cowwoc" % "diff-match-patch" % "1.1" % "test"
)

fork in Test := true // without this RAML parser tests will fail

publishTo := Some("Innova plugins repo" at "http://repproxy.srv.inn.ru/artifactory/plugins-release-local")