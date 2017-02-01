name := "flint-server"

fork := true

run := { (run in Compile).partialInput(" -i").evaluated }

javaOptions ++= Seq(
  "-Dconfig.file=../conf/server.conf",
  "-Dakka.loglevel=error",
  "-Dlog4j.configurationFile=../conf/log4j2.xml"
)

connectInput := true

val log4jVersion = "2.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka"         %% "akka-http-core"  % "10.0.2",
  "org.apache.logging.log4j"  % "log4j-api"        % log4jVersion % "runtime",
  "org.apache.logging.log4j"  % "log4j-core"       % log4jVersion % "runtime",
  "org.apache.logging.log4j"  % "log4j-1.2-api"    % log4jVersion % "runtime",
  "org.apache.logging.log4j"  % "log4j-slf4j-impl" % log4jVersion % "runtime",
  "org.apache.httpcomponents" % "httpclient"       % "4.5.3",
  "com.github.pathikrit"      %% "better-files"    % "2.17.1",
  "io.sphere"                 %% "sphere-json"     % "0.6.8"
)

lazy val Schema = config("schema").extend(Compile).hide
ivyConfigurations += Schema
inConfig(Schema)(Defaults.configSettings)

resolvers += "vamp repo" at "https://videoamp.artifactoryonline.com/videoamp/repo/"
resolvers += Resolver.bintrayRepo("commercetools", "maven")
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
