import FlintKeys._

enablePlugins(DockerPlugin)

imageNames in docker := Seq(
  ImageName(
    namespace = Some("videoamp"),
    repository = "flint-app",
    tag = Some(version.value)
  )
)

buildOptions in docker := (buildOptions in docker).value.copy(cache = false)

lazy val uiDir = settingKey[File]("uiDir")
uiDir := baseDirectory.value / "ui"

lazy val yarn = taskKey[Unit]("yarn")
yarn := {
  val exitCode = Process("yarn" :: "--pure-lockfile" :: Nil, uiDir.value) !

  if (exitCode != 0) {
    throw new RuntimeException(s""""yarn" exited with exit code $exitCode""")
  }
}

lazy val yarnBuild = taskKey[Unit]("yarnBuild")
yarnBuild := {
  val exitCode = Process(
    "yarn" :: "build" :: Nil,
    uiDir.value,
    "BABEL_ENV" -> "production"
  ) !

  if (exitCode != 0) {
    throw new RuntimeException(s""""yarn build" exited with exit code $exitCode""")
  }
}

yarnBuild := (yarnBuild dependsOn yarn).value

dockerfile in docker := {
  val serverImage = (docker in LocalProject("server")).value.toString

  new Dockerfile {
    from(serverImage)
    runRaw("apk add lighttpd")
    stageFile(baseDirectory.value / "docker", "docker")
    copyRaw("docker/launch-flint.sh", "/usr/bin")
    stageFile(uiDir.value / "build", "build")
    copyRaw("build", "/var/www/localhost/htdocs")
    expose(80)
    entryPoint("launch-flint.sh", flintServerAPIVersion.value.toString)
  }
}

docker := (docker dependsOn yarnBuild).value
