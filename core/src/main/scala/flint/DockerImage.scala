package flint

case class DockerImage(repo: String, tag: String) {
  lazy val canonicalName = repo + "/" + tag
}

object DockerImage {
  def apply(canonicalName: String): DockerImage =
    DockerImage(canonicalName.takeWhile(_ != '/'), canonicalName.dropWhile(_ != '/').drop(1))
}
