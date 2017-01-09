import org.scalastyle.sbt.ScalastylePlugin.scalastyle

scalastyleFailOnError := true

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
compileScalastyle := scalastyle.in(Compile).toTask("").value
(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value

lazy val testScalastyle = taskKey[Unit]("testScalastyle")
testScalastyle := scalastyle.in(Test).toTask("").value
(compile in Test) := ((compile in Test) dependsOn testScalastyle).value
