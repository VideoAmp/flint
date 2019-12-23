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
    from("frolvlad/alpine-glibc:alpine-3.9_glibc-2.29")
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
