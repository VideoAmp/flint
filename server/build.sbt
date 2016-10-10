name := "flint-server"

fork := true

javaOptions ++= Seq(
  "-Dconfig.file=../conf/server.conf",
  "-Dakka.loglevel=error"
)

connectInput := true

libraryDependencies += "org.apache.logging.log4j" % "log4j-1.2-api" % "2.7"
