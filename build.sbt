import FlintKeys._

lazy val commonSettings = Seq(
  organization := "com.videoamp",
  scalaVersion := "2.12.2",
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
  scalacOptions in (Compile, console) := Seq("-language:_"),
  publishTo := {
    val vamp = "https://videoamp.jfrog.io/videoamp/"

    if (isSnapshot.value)
      Some("snapshots" at vamp + "snapshot")
    else
      Some("releases" at vamp + "release")
  }
)

lazy val disablePublishing = Seq(publishArtifact := false, publish := {}, publishLocal := {})

lazy val root = (project in file("."))
  .aggregate(core, server)
  .settings(commonSettings: _*)
  .settings(disablePublishing: _*)

scalastyle := {}
scalastyle in Test := {}

disablePlugins(sbtassembly.AssemblyPlugin)

lazy val scalastyleSbt = file("../sbt/scalastyle.sbt")

lazy val core =
  project
    .settings(commonSettings: _*)
    .disablePlugins(sbtassembly.AssemblyPlugin)
    .addSbtFiles(scalastyleSbt)
lazy val server =
  project
    .dependsOn(core % "compile;test->test")
    .settings(commonSettings: _*)
    .settings(disablePublishing: _*)
    .settings(flintServerAPIVersion := 2)
    .addSbtFiles(scalastyleSbt)
