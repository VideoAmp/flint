import FlintKeys._

flintServerHost := None
flintServerUseTLS := false
flintClientAPIVersion := (flintServerAPIVersion in LocalProject("server")).value

enablePlugins(DockerPlugin)

imageNames in docker := Seq(
  ImageName(
    namespace = Some("videoamp"),
    repository = "flint-app",
    tag = Some(version.value)
  )
)

buildOptions in docker := (buildOptions in docker).value.copy(cache = false)

dockerfile in docker := {
  flintServerHost.value match {
    case Some(flintServerHost) =>
      val (flintServiceURL, flintMessagingURL) = {
        val (flintServiceScheme, flintMessagingScheme) =
          flintServerUseTLS.value match {
            case true  => ("https", "wss")
            case false => ("http", "ws")
          }
        val flintURLSuffix = "://" + flintServerHost + "/api/version/" +
            flintClientAPIVersion.value
        (flintServiceScheme + flintURLSuffix, flintMessagingScheme + flintURLSuffix)
      }

      val serverImage = (docker in LocalProject("server")).value.toString
      val uiDir       = baseDirectory.value / "ui"

      Process(
        "yarn" :: "build" :: Nil,
        uiDir,
        "REACT_APP_FLINT_SERVER_URL"    -> flintServiceURL,
        "REACT_APP_FLINT_WEBSOCKET_URL" -> flintMessagingURL) !

      new Dockerfile {
        from(serverImage)
        runRaw("apk add lighttpd")
        stageFile(baseDirectory.value / "docker", "docker")
        copyRaw("docker/launch-flint.sh", "/usr/bin")
        stageFile(uiDir / "build", "build")
        copyRaw("build", "/var/www/localhost/htdocs")
        expose(80)
        entryPoint("launch-flint.sh")
      }
    case None =>
      throw new RuntimeException("Must set flintServerHost SettingKey to nonempty value")
  }
}
