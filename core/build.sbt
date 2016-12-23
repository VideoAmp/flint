name := "flint"

val awsSdkVersion = "1.11.73"

resolvers += Resolver.bintrayRepo("commercetools", "maven")

libraryDependencies ++= Seq(
  "com.lihaoyi"                %% "scalarx"         % "0.3.2",
  "com.github.kxbmap"          %% "configs"         % "0.4.4",
  "com.amazonaws"              % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.amazonaws"              % "aws-java-sdk-ssm" % awsSdkVersion,
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0",
  "org.apache.httpcomponents"  % "httpclient"       % "4.5.2",
  "com.github.pathikrit"       %% "better-files"    % "2.16.0",
  "io.sphere"                  %% "sphere-json"     % "0.6.8",
  "org.scalatest"              %% "scalatest"       % "3.0.1" % "test"
)
