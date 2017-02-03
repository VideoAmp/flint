package flint
package server

import docker.Token
import messaging.akka.AkkaServer
import service.aws.AwsClusterService
import service.mock.MockClusterService

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

import _root_.akka.actor.ActorSystem
import _root_.akka.stream.ActorMaterializer

import com.typesafe.config.{ Config, ConfigFactory, ConfigParseOptions }
import com.typesafe.scalalogging.LazyLogging

import configs.syntax._

object FlintServer extends LazyLogging {
  def main(args: Array[String]): Unit = {
    import FlintCtx.owner

    val interactive = args.length == 1 && args(0) == "-i"

    val configParseOptions = ConfigParseOptions.defaults.setAllowMissing(false)

    // Don't timeout an idle web socket connection
    val fallbackConfig = ConfigFactory.parseString("""akka.http.server.idle-timeout="infinite"""")
    val config =
      ConfigFactory.defaultApplication(configParseOptions).withFallback(fallbackConfig)
    validateConfig(config)

    val flintConfig  = config.get[Config]("flint").value
    val serverConfig = flintConfig.get[Config]("server").value
    val bindAddress  = serverConfig.get[String]("bind_address").value

    val bindInterface = bindAddress.takeWhile(_ != ':')
    val bindPort      = bindAddress.dropWhile(_ != ':').drop(1).toInt

    val apiRoot = "/api/version/1"

    val clusterService =
      serverConfig.get[String]("cluster_service").value match {
        case "aws"  => new AwsClusterService(flintConfig)
        case "mock" => new MockClusterService
      }
    logger.info("Using " + clusterService.getClass.getSimpleName)

    val dockerConfig    = flintConfig.get[Config]("docker").value
    val dockerImageRepo = dockerConfig.get[String]("image_repo").value
    val dockerAuthToken = dockerConfig.get[String]("auth").value
    val dockerCreds     = Token(dockerAuthToken)

    implicit val actorSystem =
      ActorSystem(
        "flint",
        config = Some(config),
        defaultExecutionContext = Some(ioExecutionContext))
    implicit val materializer = ActorMaterializer()
    import actorSystem.dispatcher

    val server: Server with Killable = AkkaServer(clusterService, dockerImageRepo, dockerCreds)
    val bindingFuture                = server.bindTo(bindInterface, bindPort, apiRoot)

    val bindingFuture2 =
      bindingFuture.map { binding =>
        logger.info(s"Flint messaging server online at ${binding.messagingUrl}")
        logger.info(s"Flint service online at ${binding.serviceUrl}")
        binding
      }

    def completeBindingFuture(bindingFuture: Future[Binding]): Unit =
      bindingFuture.flatMap(_.unbind).onComplete(_ => server.terminate)

    if (interactive) {
      completeBindingFuture(bindingFuture2.map { binding =>
        // scalastyle:off println
        println("Press RETURN to shut down")
        // scalastyle:on println
        scala.io.StdIn.readLine
        binding
      })
    } else {
      try {
        Await.ready(bindingFuture2, Duration.Inf)
      } catch {
        case _: InterruptedException =>
          completeBindingFuture(bindingFuture2)
      }
    }
  }

  private def validateConfig(config: Config): Unit = {
    validateFlintConfig(config)
    config.checkValid(ConfigFactory.defaultReference, "flint.server")
  }
}
