name := "flint"

val akkaVersion = "2.4.11"

libraryDependencies ++= Seq(
  "com.lihaoyi"                  %% "scalarx"              % "0.3.1",
  "com.typesafe.akka"            %% "akka-http-core"       % akkaVersion,
  "com.typesafe.akka"            %% "akka-http-testkit"    % akkaVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.3",
  "com.github.kxbmap"            %% "configs"              % "0.4.3",
  "com.amazonaws"                % "aws-java-sdk-ec2"      % "1.11.41",
  "com.typesafe.scala-logging"   %% "scala-logging"        % "3.5.0"
)
