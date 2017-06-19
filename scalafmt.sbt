scalafmtVersion in ThisBuild := "1.0.0-RC4"

lazy val scalafmtScope = ScopeFilter(
  inAnyProject,
  inConfigurations(Compile, Test)
)

scalafmt := scalafmt.all(scalafmtScope).value
