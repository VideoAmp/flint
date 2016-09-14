# Welcome to Flint!

Blah blah blah...

### Server Quickstart

1. Download [application-template.conf](conf/application-template.conf) to a file named `application.conf` and customize to your environment.
1. Download [creds-template.conf](conf/creds-template.conf) to a file named `creds.conf` and put your Docker and AWS credentials inside.
1. Download [server-template.conf](conf/server-template.conf) to a file named `server.conf` and customize as desired.
1. Run `sbt assembly`.
1. Run the server with
```
java -Dconfig.file=<path-to-server.conf> -jar server/target/scala-2.11/flint-server-assembly-0.1-SNAPSHOT.jar
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

Once in a REPL, you can use a `ClusterManager` to launch, manage and terminate clusters:

```scala
import flint._, service._, aws._
import java.io.File
import java.util.UUID
import com.typesafe.config.ConfigFactory
import rx.Ctx.Owner.Unsafe._

val flintConfig = ConfigFactory.parseFile(new File("conf/application.conf")).getConfig("flint")
validateFlintConfig(flintConfig)
val cm: ClusterManager = new AwsClusterManager(flintConfig)

// Define your ClusterSpec
val clusterSpec: ClusterSpec = ???
val launch = cm.launchCluster(clusterSpec)
val terminate = launch.flatMap(_.terminate)
```
