# Welcome to Flint!

Flint is a Scala library, server and Web UI for managing on-demand Spark clusters.

[![Build Status](https://semaphoreci.com/api/v1/projects/807efe73-a850-4be3-8064-dd83248bd7c2/1348779/shields_badge.svg)](https://semaphoreci.com/videoamp/flint)
[![Maven Central](https://img.shields.io/maven-central/v/com.videoamp/flint_2.12.svg)](https://repo1.maven.org/maven2/com/videoamp/flint_2.12/)

### Scala Library

Add

```scala
libraryDependencies += "com.videoamp" %% "flint" % "1.2.6"
```

to your project's `build.sbt`. See the REPL example below for a Flint code snippet. The [Scaladoc](https://videoamp.github.io/flint/latest/api/flint/) for the latest build is also hosted online.

To run Flint in a REPL, you will need configured `aws_service.conf` and `creds.conf` files. The other configuration files are unnecessary and required only by the server. See [TUTORIAL.md](TUTORIAL.MD) for guidance on creating these files for your environment.

### Web App

Follow the [Tutorial](TUTORIAL.md) for a complete guide to deploying the Flint Web app.

### REPL

There are several ways to use Flint in a REPL.

#### SBT Console

Run `sbt core/console`.

#### Ammonite

You can use the files in [this gist](https://gist.github.com/mallman/5823e1a9eb5ec72b982ea93b354769e1) to integrate [Ammonite](http://ammonite.io/) into sbt globally. Copy `ammonite.sbt` into your global sbt build directory. For example, for sbt 0.13 this is `~/.sbt/0.13`. This will add an `amm` task to your sbt build. If you run `core/amm` or `server/amm` in sbt you will drop into an Ammonite console with that project's source and library dependencies on the classpath. If you wish to run some code from a file every time you run the `amm` task, integrate `predef.sc` from the gist into `~/.ammonite/predef.sc` (or copy it over if you have none). This will load the contents of any Scala script named `.amm_init.sc` from the subproject's directory when the `amm` task is run. For example, running `core/amm` will execute the code in `core/.amm_init.sc`, if that file exists. (Note that due to https://github.com/lihaoyi/Ammonite/issues/658, this functionality is broken as of Ammonite 1.0.0. As a workaround you can run `repl.load.exec(localInitPath)` within the Ammonite console until this bug is fixed.)

#### Example

Once in a REPL (sbt or Ammonite), you can use a `ClusterService` to launch, manage and terminate clusters. For example:

```scala
import flint._, service._, aws._
import ContainerState._
import ClusterTerminationReason._
import java.io.File
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

val flintConfig = ConfigFactory.parseFile(new File("conf/aws_service.conf")).getConfig("flint")
validateFlintConfig(flintConfig)

val cs: ClusterService = new AwsClusterService(flintConfig)

def newClusterSpec(flintSparkImage: DockerImage, workerInstanceType: String, numWorkers: Int, subnetId: String) =
  ClusterSpec(
    ClusterId(),
    flintSparkImage,
    "Bob Data User",
    None,
    None,
    "t2.micro",
    workerInstanceType,
    numWorkers,
    subnetId)

// Pick one of your Flint Spark images. See https://github.com/VideoAmp/flint-spark for more info
val flintSparkImage: DockerImage = ???
// Pick one of your VPC subnet ids
val subnetId: String = ???
val spec = newClusterSpec(flintSparkImage, "c3.8xlarge", 4, subnetId)

// Launch it
cs.launchCluster(spec).foreach { managedCluster =>
  managedCluster.cluster.master.containerState.collectFirst { case ContainerRunning =>
    println("Compute, compute, compute...")
    Thread.sleep(3000)
    println("Terminating...")
    Await.ready(managedCluster.terminate(ClientRequested))
  }
}
```

### Contributing

If you'd like to contribute to the Flint codebase, please read about the contribution process in [CONTRIBUTING.md](CONTRIBUTING.md). Then consult [DEVELOPING.md](DEVELOPING.md) for build instructions and guidance.
