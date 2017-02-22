name := "flint"

val awsSdkVersion = "1.11.94"

libraryDependencies ++= Seq(
  "com.lihaoyi"                %% "scalarx"         % "0.3.2",
  "com.github.kxbmap"          %% "configs"         % "0.4.4",
  "com.amazonaws"              % "aws-java-sdk-ec2" % awsSdkVersion,
  "com.amazonaws"              % "aws-java-sdk-ssm" % awsSdkVersion,
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0",
  "org.scalatest"              %% "scalatest"       % "3.0.1" % "test"
)
