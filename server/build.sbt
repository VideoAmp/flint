name := "flint-server"

fork := true

javaOptions ++= Seq(
  "-Dconfig.file=../conf/server.conf",
  "-Dakka.loglevel=error"
)

connectInput := true

val akkaVersion = "2.4.11"

resolvers += Resolver.bintrayRepo("commercetools", "maven")

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-http-core"    % akkaVersion,
  "com.typesafe.akka"        %% "akka-http-testkit" % akkaVersion,
  "io.sphere"                %% "sphere-json"       % "0.6.5",
  "org.apache.logging.log4j" % "log4j-1.2-api"      % "2.7"
)
