# Welcome to Flint!

Flint is a Scala library for managing on-demand Spark clusters. It includes a WebSocket server for remote interaction.

### Server Quickstart

1. Download [application-template.conf](conf/application-template.conf) to a file named `application.conf` and customize to your environment.
1. Download [creds-template.conf](conf/creds-template.conf) to a file named `creds.conf` and put your Docker and AWS credentials inside.
1. Download [server-template.conf](conf/server-template.conf) to a file named `server.conf` and customize as desired.
1. Run `sbt assembly`.
1. Run the server with
```
java -Dconfig.file=<path-to-server.conf> -jar server/target/scala-2.11/flint-server-assembly-*.jar
```

### Scala Library Quickstart

Add

```scala
libraryDependencies += "com.videoamp" %% "flint" % "0.1-SNAPSHOT"
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
import rx.Ctx.Owner.Unsafe._

implicit val ec = flint.flintExecutionContext

val flintConfig = ConfigFactory.parseFile(new File("conf/application.conf")).getConfig("flint")
validateFlintConfig(flintConfig)

val cs: ClusterService = new AwsClusterService(flintConfig)

// Define your ClusterSpec
val clusterSpec =
  ClusterSpec(
    ClusterId(),
    DockerImage("videoamp/spark", "2.0.1-SNAPSHOT-2.6.0-cdh5.5.1-b49-9273bdd-92"),
    "Michael",
    None,
    None,
    "t2.micro",
    "t2.micro",
    2)

// Launch it
cs.launchCluster(clusterSpec).foreach { managedCluster =>
  managedCluster.cluster.lifecycleState.foreach { state =>
    if (state == Running) {
      println("Compute, compute, compute...")
      Thread.sleep(3000)
      println("Terminating...")
      managedCluster.terminate
    }
  }
}
```

### Contributing to Flint

If you'd like to contribute to the Flint codebase, please read about the contribution process in [CONTRIBUTING.md](CONTRIBUTING.md). Then consult [DEVELOPING.md](DEVELOPING.md) for tips for hacking on the codebase.
