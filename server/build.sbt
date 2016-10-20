name := "flint-server"

fork := true

javaOptions ++= Seq(
  "-Dconfig.file=../conf/server.conf",
  "-Dakka.loglevel=error",
  "-Dlog4j.configurationFile=../conf/log4j2.xml"
)

connectInput := true

val akkaVersion = "2.4.11"

resolvers += Resolver.bintrayRepo("commercetools", "maven")

val log4jVersion = "2.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-http-core"    % akkaVersion,
  "com.typesafe.akka"        %% "akka-http-testkit" % akkaVersion,
  "io.sphere"                %% "sphere-json"       % "0.6.5",
  "org.apache.logging.log4j" % "log4j-api"          % log4jVersion % "runtime",
  "org.apache.logging.log4j" % "log4j-core"         % log4jVersion % "runtime",
  "org.apache.logging.log4j" % "log4j-1.2-api"      % log4jVersion % "runtime",
  "org.apache.logging.log4j" % "log4j-slf4j-impl"   % log4jVersion % "runtime"
)
