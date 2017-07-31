# Welcome to Flint!

Flint is a Scala library, server and Web UI for managing on-demand Spark clusters.

### Docker Image Build and Deployment

While the server can be built and deployed independently of the Web UI, we will focus on deploying the entire app. For this purpose you will need

1. a trusted server to deploy to, with ports 80 and 8080 free,
1. [sbt](https://github.com/paulp/sbt-extras),
1. [Docker](https://www.docker.com), and
1. push access to your Docker image repo.

For the sake of these instructions, we will assume that the hostname of the server to which you plan to deploy Flint is `flint.foo.internal`. Flint does not support authentication or SSL connections out of the box, so it must be deployed to a trusted server. Build and push the Flint app Docker image by running
```sh
sbt 'set FlintKeys.flintServerHost := Some("flint.foo.internal:8080")' dockerBuildAndPush
```

You will need to configure the Flint server before starting it. Copy the configuration template files from the [conf/](conf/) directory to a directory on your deployment server. For the sake of this example, we will assume that directory is `/root/flint-conf`. Rename each file by removing the `-template` string from its name and fill in the configuration settings. All of these files use the [HOCON](https://github.com/typesafehub/config/blob/master/HOCON.md) configuration syntax except for `log4j2.xml`. See http://logging.apache.org/log4j/2.x/manual/configuration.html for more information on configuring logging.

On the deployment server do a `docker pull videoamp/flint-app:<version>` to download the Flint app image. Then start Flint with
```sh
docker run -d --name flint-app -v /root/flint-conf:/conf -p 8080:8080 -p 80:80 videoamp/flint-app:<version>
```

Browsing to `http://flint.foo.internal/` should bring up the Flint UI. If anything goes wrong, check the server logs with
```sh
docker logs flint-app
```
and check your browser's JavaScript console.

### Scala Library

Add

```scala
libraryDependencies += "com.videoamp" %% "flint" % version
```

to your project's `build.sbt`. See the REPL example below for a Flint code snippet.

You will need to configure Flint for your Docker and AWS environment. Flint uses [Typesafe Config](https://github.com/typesafehub/config). Take a look at the configuration template files in [conf/](conf/) for guidance.

### REPL

There are several ways to use Flint in a REPL.

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

### Contributing

If you'd like to contribute to the Flint codebase, please read about the contribution process in [CONTRIBUTING.md](CONTRIBUTING.md). Then consult [DEVELOPING.md](DEVELOPING.md) for tips for hacking on the codebase.
