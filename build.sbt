lazy val commonSettings = Seq(
  organization := "com.videoamp",
  scalaVersion := "2.11.8",
  javacOptions := Seq("-g"),
  scalacOptions := Seq(
    "-deprecation",
    "-language:implicitConversions",
    "-feature",
    "-unchecked",
    "-Xlint:_",
    "-Ywarn-adapted-args",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-unused-import"),
  scalacOptions in (Compile, console) := Seq("-language:_"))

lazy val root = (project in file("."))
  .aggregate(core, server)
  .settings(commonSettings: _*)
  .settings(
    aggregate in publish := false
  )
  .disablePlugins(sbtassembly.AssemblyPlugin)

lazy val scalastyleSbt = file("../scalastyle.sbt")

lazy val core =
  project
    .settings(commonSettings: _*)
    .addSbtFiles(scalastyleSbt)
    .disablePlugins(sbtassembly.AssemblyPlugin)
lazy val server =
  project
    .dependsOn(core % "compile;test->test")
    .settings(commonSettings: _*)
    .addSbtFiles(scalastyleSbt)
