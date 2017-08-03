import sbt._

object FlintKeys {
  val flintServerAPIVersion = settingKey[Int]("The Flint server API version")
}
