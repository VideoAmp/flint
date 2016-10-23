scalafmtConfig in ThisBuild := Some(file(".scalafmt.conf"))

lazy val scalafmtScope = ScopeFilter(
  inAnyProject,
  inConfigurations(Compile, Test)
)

scalafmt := scalafmt.all(scalafmtScope).value
