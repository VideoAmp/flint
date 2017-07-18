scalafmtVersion in ThisBuild := "1.1.0"

lazy val scalafmtScope = ScopeFilter(
  inAnyProject,
  inConfigurations(Compile, Test, Sbt)
)

scalafmt := scalafmt.all(scalafmtScope).value
