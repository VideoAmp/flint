enablePlugins(DockerPlugin)

docker := (docker dependsOn assembly).value

imageNames in docker := Seq(
  ImageName(
    namespace = Some("videoamp"),
    repository = name.value,
    tag = Some(version.value)
  )
)

buildOptions in docker := (buildOptions in docker).value.copy(cache = false)

dockerfile in docker := {
  val artifact           = (assemblyOutputPath in assembly).value
  val artifactTargetPath = name.value + "-assembly.jar"

  new Dockerfile {
    from("adoptopenjdk/openjdk11:x86_64-alpine-jdk-11.0.5_10")
    runRaw("apk update")
    copy(artifact, artifactTargetPath)
    expose(8080)
    entryPoint(
      "env",
      "java",
      "-Dconfig.file=conf/server.conf",
      "-Dakka.loglevel=error",
      "-Dlog4j.configurationFile=conf/log4j2.xml",
      "-jar",
      artifactTargetPath
    )
  }
}
