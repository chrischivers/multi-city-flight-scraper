name := "multi-city-flight-scraper"

version := "0.1"

scalaVersion := "2.12.10"

val http4sVersion = "0.21.0-M6"
val circeVersion  = "0.12.3"

libraryDependencies ++= Seq(
  "org.typelevel"           %% "cats-effect"    % "2.0.0",
  "org.seleniumhq.selenium" % "selenium-java"   % "3.141.59",
  "io.chrisdavenport"       %% "log4cats-slf4j" % "1.0.1",
  "ch.qos.logback"          % "logback-classic" % "1.2.3",
  "org.scalatest"           %% "scalatest"      % "3.0.8" % Test,
  "org.scalacheck"          %% "scalacheck"     % "1.14.1" % "test"
)

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl"          % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion,
  "org.http4s" %% "http4s-circe"        % http4sVersion
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)
