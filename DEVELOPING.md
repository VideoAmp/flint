## Developing for Flint

This documentation is for developers who wish to hack on the Flint codebase itself.

### Building Flint

Flint is built with [sbt](http://www.scala-sbt.org/). We recommend using the sbt runner from [sbt-extras](https://github.com/paulp/sbt-extras), but the standard runner will do.

In addition to `compile` and `test`, the most important sbt tasks for development are `scalafmt` and `scalastyle`. The former runs the [scalafmt](http://scalafmt.org/) code formatter and the latter runs the [scalastyle](http://scalastyle.org/) code style checker.

### Editors and IDEs

Flint is an editor-agnostic codebase. Please do not commit any tool-specific files.
