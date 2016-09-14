name := "flint-server"

fork := true

javaOptions ++= Seq(
  "-Dconfig.file=../conf/server.conf",
  "-Dakka.loglevel=error"
)

connectInput := true
