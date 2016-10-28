import com.typesafe.sbt.SbtGit.GitKeys._

enablePlugins(BuildInfoPlugin)

buildInfoPackage := "flint"

buildInfoKeys := Seq[BuildInfoKey](
  name,
  version,
  scalaVersion,
  BuildInfoKey.map(gitHeadCommit) { case (k, v) => k -> v.get }
)

buildInfoOptions += BuildInfoOption.ToJson
buildInfoOptions += BuildInfoOption.ToMap
