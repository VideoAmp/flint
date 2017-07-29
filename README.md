# Welcome to Flint!

Flint is a Scala library for managing on-demand Spark clusters. It includes a WebSocket server for remote interaction.

### Server Quickstart

1. Download [server-template.conf](conf/server-template.conf) to a file named `server.conf` and customize as desired.
1. Download [aws_service-template.conf](conf/aws_service-template.conf) to a file named `aws_service.conf` and customize to your environment.
1. Download [creds-template.conf](conf/creds-template.conf) to a file named `creds.conf` and put your Docker and AWS credentials inside.
1. Download [docker-template.conf](conf/docker-template.conf) to a file named `docker.conf` and put the name of your Spark Docker repo inside, likely `videoamp/spark`.
1. Download [log4j2-example.xml](conf/log4j2-example.xml) to a file named `log4j2.xml` and customize as desired.
1. Run `sbt assembly`.
1. Run the server with
```
java -Dconfig.file=<path-to-server.conf> -Dakka.loglevel=error -Dlog4j.configurationFile=<path-to-log4j2.xml> -jar server/target/scala-2.11/flint-server-assembly-*.jar
```

An example logging configuration file is provided in [conf/log4j2-example.xml](conf/log4j2-example.xml). See http://logging.apache.org/log4j/2.x/manual/configuration.html for more information on configuring the logger.

The Flint server uses [Akka](http://akka.io/), which has its own logging configuration. For the sake of simplicity, you can adjust the default Akka log level by specifying the `akka.loglevel` system property, e.g. `-Dakka.loglevel=error`.

### Scala Library Quickstart

Add

```scala
libraryDependencies += "com.videoamp" %% "flint" % version
```

to your project's `build.sbt`. See the REPL example below for a Flint code snippet.

You will need to configure Flint for your Docker and AWS environment. Flint uses [Typesafe Config](https://github.com/typesafehub/config). Take a look at the configuration template files in [conf/](conf/) for guidance.

### REPL Quickstart

There are several ways to access Flint in a REPL.

#### SBT Console

Run `sbt core/console`.

#### Ammonite

You can use the files in https://gist.github.com/mallman/5823e1a9eb5ec72b982ea93b354769e1 to integrate [Ammonite](http://ammonite.io/) into sbt globally. Copy `ammonite.sbt` into your global sbt build directory. For example, for sbt 0.13 this is `~/.sbt/0.13`. This will add an `amm` task to your sbt build. If you run `core/amm` or `server/amm` in sbt you will drop into an Ammonite console with that project's source and library dependencies on the classpath. If you wish to run some code from a file every time you run the `amm` task, integrate `predef.sc` from the gist into `~/.ammonite/predef.sc` (or copy it over if you have none). This will load the contents of the Scala script `.amm_init.sc` from the subproject's directory when the `amm` task is run. For example, running `core/amm` will execute the code in `core/.amm_init.sc`, if that file exists. (Note that due to https://github.com/lihaoyi/Ammonite/issues/658, this functionality is broken in Ammonite 1.0.0 and 1.0.1. As a workaround you can run `repl.load.exec(localInitPath)` within the Ammonite console until this bug is fixed.)

Once in a REPL, you can use a `ClusterService` to launch, manage and terminate clusters:

```scala
import flint._, service._, aws._
import java.io.File
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

val flintConfig = ConfigFactory.parseFile(new File("conf/aws_service.conf")).getConfig("flint")
validateFlintConfig(flintConfig)

val cs: ClusterService = new AwsClusterService(flintConfig)

def newClusterSpec(imageTag: String, workerInstanceType: String, numWorkers: Int, subnetId: String) =
  ClusterSpec(
    ClusterId(),
    DockerImage("videoamp/spark", imageTag),
    "Bob Data User",
    None,
    None,
    "t2.micro",
    workerInstanceType,
    numWorkers,
    subnetId)

// Pick one of your VPC subnet ids
val subnetId: String = ???
val spec = newClusterSpec("2.1.0-SNAPSHOT-2.6.0-cdh5.5.1-b20-cf492a7-139", "c3.8xlarge", 4, subnetId)

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

### Contributing to Flint

If you'd like to contribute to the Flint codebase, please read about the contribution process in [CONTRIBUTING.md](CONTRIBUTING.md). Then consult [DEVELOPING.md](DEVELOPING.md) for tips for hacking on the codebase.
