import com.typesafe.sbt.SbtGit.GitKeys._
import FlintKeys._

enablePlugins(BuildInfoPlugin)

buildInfoObject := "ServerBuildInfo"
buildInfoPackage := "flint.server"

buildInfoKeys := Seq[BuildInfoKey](
  flintServerAPIVersion
)

buildInfoOptions += BuildInfoOption.ToJson
buildInfoOptions += BuildInfoOption.ToMap
