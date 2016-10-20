name := "flint"

libraryDependencies ++= Seq(
  "com.lihaoyi"                %% "scalarx"         % "0.3.1",
  "com.github.kxbmap"          %% "configs"         % "0.4.3",
  "com.amazonaws"              % "aws-java-sdk-ec2" % "1.11.43",
  "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0",
  "org.scalatest"              %% "scalatest"       % "3.0.0" % "test"
)
