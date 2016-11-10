name := "flint-server"

fork := true

javaOptions ++= Seq(
  "-Dconfig.file=../conf/server.conf",
  "-Dakka.loglevel=error",
  "-Dlog4j.configurationFile=../conf/log4j2.xml"
)

connectInput := true

val akkaVersion  = "2.4.11"
val log4jVersion = "2.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-http-core"    % akkaVersion,
  "com.typesafe.akka"        %% "akka-http-testkit" % akkaVersion,
  "org.apache.logging.log4j" % "log4j-api"          % log4jVersion % "runtime",
  "org.apache.logging.log4j" % "log4j-core"         % log4jVersion % "runtime",
  "org.apache.logging.log4j" % "log4j-1.2-api"      % log4jVersion % "runtime",
  "org.apache.logging.log4j" % "log4j-slf4j-impl"   % log4jVersion % "runtime"
)

lazy val Schema = config("schema").extend(Compile).hide
ivyConfigurations += Schema
inConfig(Schema)(Defaults.configSettings)

resolvers += "vamp repo" at "https://videoamp.artifactoryonline.com/videoamp/repo/"
libraryDependencies += "com.sauldhernandez" %% "autoschema" % "1.0.3" % Schema

inConfig(Schema)(ScalaFmtPlugin.configScalafmtSettings)

lazy val scalafmtScope = ScopeFilter(
  inProjects(ThisProject),
  inConfigurations(Compile, Test, Schema)
)

scalafmt := scalafmt.all(scalafmtScope).value

inConfig(Schema)(rawScalastyleSettings)
scalastyleSources in Schema := Seq((scalaSource in Schema).value)

lazy val schemaScalastyle = taskKey[Unit]("schemaScalastyle")
schemaScalastyle := scalastyle.in(Schema).toTask("").value
(compile in Schema) <<= (compile in Schema) dependsOn schemaScalastyle
