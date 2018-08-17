import FlintKeys._

val APIVersion = 3

lazy val commonSettings = Seq(
  organization := "com.videoamp",
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
  javacOptions := Seq("-g"),
  scalacOptions := Seq(
    "-deprecation",
    "-language:implicitConversions",
    "-language:postfixOps",
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
    "-Ywarn-unused-import"
  ),
  scalacOptions in (Compile, console) := Seq("-language:_")
)

lazy val publicationSettings = Seq(
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  publishMavenStyle := true,
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url("https://github.com/VideoAmp/flint")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/VideoAmp/flint"),
      "scm:git@github.com:VideoAmp/flint.git"
    )
  ),
  developers := List(
    Developer(
      id = "mallman",
      name = "Michael Allman",
      email = "msa@allman.ms",
      url = url("https://github.com/mallman"))
  )
)

lazy val disablePublishing = Seq(publishArtifact := false, publish := {}, publishLocal := {})

lazy val root = (project in file("."))
  .aggregate(core, server)
  .disablePlugins(SbtPgp)
  .settings(commonSettings: _*)
  .settings(disablePublishing: _*)
  .settings(flintServerAPIVersion := APIVersion)

scalastyle := {}
scalastyle in Test := {}

disablePlugins(sbtassembly.AssemblyPlugin)

lazy val scalastyleSbt = file("../sbt/scalastyle.sbt")

lazy val core =
  project
    .settings(commonSettings: _*)
    .settings(publicationSettings: _*)
    .disablePlugins(sbtassembly.AssemblyPlugin)
    .addSbtFiles(scalastyleSbt)
lazy val server =
  project
    .dependsOn(core % "compile;test->test")
    .disablePlugins(SbtPgp)
    .settings(commonSettings: _*)
    .settings(disablePublishing: _*)
    .settings(flintServerAPIVersion := APIVersion)
    .addSbtFiles(scalastyleSbt)
