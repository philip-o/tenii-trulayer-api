
name := "tenii-trulayer-api"

version := "0.1"

scalaVersion := "2.12.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "Artifactory" at "https://eurostar.jfrog.io/eurostar/libs-release/"

val akkaV       = "2.4.19"
val akkaHttpV   = "10.0.8"
val scalaTestV  = "3.0.3"
val scalamockV  = "3.6.0"
val circeV     = "0.8.0"
val sttpVersion = "0.0.14"

libraryDependencies ++= Seq(
  "org.mongodb" %% "casbah" % "3.1.1",
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,

  // test
  "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test",
  "org.scalatest"     %% "scalatest" % scalaTestV % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % scalamockV % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",

  // json
  "de.heikoseeberger" %% "akka-http-circe" % "1.17.0",
  "io.circe" %% "circe-core" % circeV,
  "io.circe" %% "circe-generic" % circeV,
  "io.circe" %% "circe-parser" % circeV,
  "io.circe" %% "circe-jawn" % circeV,

  // logging
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" % "logback-classic" % "1.2.1",

  // swagger
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.9.1",
  "co.pragmati" %% "swagger-ui-akka-http" % "1.0.0",

  // sttp
  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "com.softwaremill.sttp" %% "akka-http-backend" % sttpVersion,
  "com.softwaremill.sttp" %% "circe" % sttpVersion
)

enablePlugins(JavaAppPackaging)