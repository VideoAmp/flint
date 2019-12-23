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
    from("anapsix/alpine-java:8u202b08_jdk_unlimited")
    runRaw("apk update")
    runRaw("apk add jemalloc libgcc")
    copy(artifact, artifactTargetPath)
    expose(8080)
    entryPoint(
      "env",
      "LD_PRELOAD=/usr/lib/libjemalloc.so.2",
      "java",
      "-Dconfig.file=conf/server.conf",
      "-Dakka.loglevel=error",
      "-Dlog4j.configurationFile=conf/log4j2.xml",
      "-jar",
      artifactTargetPath
    )
  }
}
