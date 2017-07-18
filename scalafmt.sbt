scalafmtVersion in ThisBuild := "1.1.0"

lazy val scalafmtScope = ScopeFilter(
  inAnyProject,
  inConfigurations(Compile, Test)
)

scalafmt := scalafmt.all(scalafmtScope).value
