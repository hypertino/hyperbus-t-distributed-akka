lazy val commonSettings = Seq(
  version := "0.2-SNAPSHOT",
  organization := "com.hypertino",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-optimise",
    "-target:jvm-1.8",
    "-encoding", "UTF-8"
  ),
  javacOptions ++= Seq(
    "-source", "1.8",
    "-target", "1.8",
    "-encoding", "UTF-8",
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public")
  ),
  libraryDependencies += {
    macroParadise
  }
)

// external dependencies
lazy val binders = "com.hypertino" %% "binders" % "1.0-SNAPSHOT"
lazy val jsonBinders = "com.hypertino" %% "json-binders" % "1.0-SNAPSHOT"
lazy val configBinders = "com.hypertino" %% "typesafe-config-binders" % "0.13-SNAPSHOT"
lazy val scalaMock = "com.hypertino" %% "scalamock-scalatest-support" % "3.4-SNAPSHOT" % "test"
lazy val rxscala = "io.reactivex" %% "rxscala" % "0.26.3"
lazy val slf4j = "org.slf4j" % "slf4j-api" % "1.7.12"
lazy val akka = "com.typesafe.akka" %% "akka-actor" % "2.4.1"
lazy val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.4.1"
lazy val akkaContrib = "com.typesafe.akka" %% "akka-contrib" % "2.4.1"
lazy val akkaTest = "com.typesafe.akka" %% "akka-testkit" % "2.4.1" % "test"
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"
lazy val scalaArm = "com.jsuereth" %% "scala-arm" % "1.4"
lazy val apacheCommons = "org.apache.directory.studio" % "org.apache.commons.io" % "2.4"
lazy val kafkaClient = "org.apache.kafka" % "kafka-clients" % "0.8.2.1"
lazy val kafka =  "org.apache.kafka" %% "kafka" % "0.8.2.1" exclude("org.slf4j", "slf4j-log4j12")
lazy val serviceConfig = "com.hypertino" %% "service-config" % "0.2-SNAPSHOT"
lazy val serviceControl = "com.hypertino" %% "service-control" % "0.3-SNAPSHOT"
lazy val jline = "jline" % "jline" % "2.12.1"
lazy val ramlParser = "com.hypertino" % "raml-parser-2" % "1.0.5-SNAPSHOT"
lazy val diffMatchPatch = "org.bitbucket.cowwoc" % "diff-match-patch" % "1.1"
lazy val quasiQuotes = "org.scalamacros" %% "quasiquotes" % "2.1.0" cross CrossVersion.binary
lazy val macroParadise = compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

lazy val `hyperbus-transport` = project in file("hyperbus-transport") settings (
    commonSettings,
    name := "hyperbus-transport",
    libraryDependencies ++= Seq(
      binders,
      jsonBinders,
      configBinders,
      rxscala,
      slf4j,
      scalaMock
    )
  )

lazy val `hyperbus-model` = project in file("hyperbus-model") settings (
    commonSettings,
    name := "hyperbus-model",
    libraryDependencies ++= Seq(
      binders,
      slf4j,
      scalaMock
    )
  ) dependsOn `hyperbus-transport`

lazy val `hyperbus` = project in file("hyperbus") settings (
    commonSettings,
    name := "hyperbus",
    libraryDependencies ++= Seq(
      rxscala,
      binders,
      scalaMock
    )
  ) dependsOn(`hyperbus-model`, `hyperbus-t-inproc`)

lazy val `hyperbus-akka` = project in file("hyperbus-akka") settings (
    commonSettings,
    name := "hyperbus-akka",
    libraryDependencies ++= Seq(
      slf4j,
      scalaMock,
      akka,
      akkaSlf4j,
      akkaTest
    )
  ) dependsOn `hyperbus`

lazy val `hyperbus-t-inproc` = project in file("hyperbus-t-inproc") settings (
    commonSettings,
    name := "hyperbus-t-inproc",
    libraryDependencies ++= Seq(
      slf4j,
      scalaMock,
      logback % "test"
    )
  ) dependsOn `hyperbus-transport`

lazy val `hyperbus-t-distributed-akka` = project in file("hyperbus-t-distributed-akka") settings (
    commonSettings,
    name := "hyperbus-t-distributed-akka",
    libraryDependencies ++= Seq(
      slf4j,
      scalaMock,
      scalaArm,
      akka,
      akkaSlf4j,
      akkaContrib,
      akkaTest,
      apacheCommons % "test",
      logback % "test"
    )
  ) dependsOn(`hyperbus-transport`, `hyperbus-model`)
  
lazy val `hyperbus-t-kafka` = project in file("hyperbus-t-kafka") settings (
    commonSettings,
    name := "hyperbus-t-kafka",
    libraryDependencies ++= Seq(
      kafka,
      kafkaClient,
      slf4j,
      scalaMock,
      scalaArm,
      apacheCommons % "test",
      logback % "test"
    )
  ) dependsOn(`hyperbus-transport`, `hyperbus-model`)

lazy val `hyperbus-cli` = project in file("hyperbus-cli") settings (
    commonSettings,
    name := "hyperbus-t-kafka",
    libraryDependencies ++= Seq(
      jline,
      binders,
      serviceControl,
      serviceConfig,
      slf4j,
      logback,
      scalaMock,
      akka,
      akkaSlf4j,
      akkaTest
    )
  ) dependsOn(`hyperbus`, `hyperbus-t-distributed-akka`)

lazy val `hyperbus-sbt-plugin` = project in file("hyperbus-sbt-plugin") settings (
    sbtPlugin := true,
    scalaVersion := "2.10.6",
    organization := "com.hypertino",
    name := "hyperbus-sbt-plugin",
    scalacOptions := Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-optimise",
      "-target:jvm-1.7",
      "-encoding", "UTF-8"
    ),
    libraryDependencies ++= Seq(
      binders,
      ramlParser,
      slf4j,
      scalaMock,
      diffMatchPatch % "test"
    ),
    fork in Test := true,
    resolvers ++= Seq(
      Resolver.sonatypeRepo("public")
    )
  )

lazy val `hyperbus-root` = project.in(file(".")) aggregate(
  `hyperbus-transport`,
  `hyperbus-model`,
  `hyperbus`,
  `hyperbus-akka`,
  `hyperbus-t-inproc`,
  `hyperbus-t-distributed-akka`,
  `hyperbus-t-kafka`,
  `hyperbus-cli`,
  `hyperbus-sbt-plugin`
  )