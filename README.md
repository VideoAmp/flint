# Welcome to Flint!

Flint is a Scala library for managing on-demand Spark clusters. It includes a WebSocket server for remote interaction.

### Server Quickstart

1. Download [server-template.conf](conf/server-template.conf) to a file named `server.conf` and customize as desired.
1. Download [aws_service-template.conf](conf/aws_service-template.conf) to a file named `aws_service.conf` and customize to your environment.
1. Download [creds-template.conf](conf/creds-template.conf) to a file named `creds.conf` and put your Docker and AWS credentials inside.
1. Run `sbt assembly`.
1. Run the server with
```
java -Dconfig.file=<path-to-server.conf> -jar server/target/scala-2.11/flint-server-assembly-*.jar
```

A default logging configuration file is provided in [conf/log4j2.xml](conf/log4j2.xml). See http://logging.apache.org/log4j/2.x/manual/configuration.html for more information on configuring the logger.

The Flint server uses [Akka](http://akka.io/), which has its own logging configuration. For the sake of simplicity, you can adjust the default Akka log level by specifying the `akka.loglevel` system property, e.g. `-Dakka.loglevel=error`.

### Scala Library Quickstart

Add

```scala
libraryDependencies += "com.videoamp" %% "flint" % "1.0-SNAPSHOT"
```

to your project's `build.sbt`. See the REPL example below for a Flint code snippet.

You will need to configure Flint for your Docker and AWS environment. Flint uses [Typesafe Config](https://github.com/typesafehub/config). Take a look at the configuration template files in [conf/](conf/) for guidance.

### REPL Quickstart

There are several ways to access Flint in a REPL.

#### SBT Console

Run `sbt core/console`.

#### Ammonite

(Docs to come...)

Once in a REPL, you can use a `ClusterService` to launch, manage and terminate clusters:

```scala
import flint._, service._, aws._
import java.io.File
import com.typesafe.config.ConfigFactory

import concurrent.Await
import concurrent.duration._

implicit val ec = flint.flintExecutionContext

val flintConfig = ConfigFactory.parseFile(new File("conf/aws_service.conf")).getConfig("flint")
validateFlintConfig(flintConfig)

val cs: ClusterService = new AwsClusterService(flintConfig)

def newClusterSpec(imageTag: String, workerInstanceType: String, numWorkers: Int) =
  ClusterSpec(
    ClusterId(),
    DockerImage("videoamp/spark", imageTag),
    "Bob Data User",
    None,
    None,
    "t2.micro",
    workerInstanceType,
    numWorkers)

val spec = newClusterSpec("2.1.0-SNAPSHOT-2.6.0-cdh5.5.1-b20-cf492a7-139", "c3.8xlarge", 4)

// Launch it
cs.launchCluster(spec).foreach { managedCluster =>
  managedCluster.cluster.containerState.collectFirst { case ContainerRunning =>
    println("Compute, compute, compute...")
    Thread.sleep(3000)
    println("Terminating...")
    Await.ready(managedCluster.terminate)
  }
}
```

### Contributing to Flint

If you'd like to contribute to the Flint codebase, please read about the contribution process in [CONTRIBUTING.md](CONTRIBUTING.md). Then consult [DEVELOPING.md](DEVELOPING.md) for tips for hacking on the codebase.
