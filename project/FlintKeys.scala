import sbt._

object FlintKeys {
  val flintServerAPIVersion = settingKey[Int]("The Flint server API version")
  val flintServerHost = settingKey[Option[String]]("The host and port where the Flint Server is deployed, formatted as host[:port]")
  val flintServerUseTLS =
    settingKey[Boolean]("Whether the Flint Server is deployed with a TLS endpoint")
  val flintClientAPIVersion = settingKey[Int]("The Flint client API version")
}
