package flint
package server

import messaging.akka.AkkaServer
import service.aws.AwsClusterService
import service.mock.MockClusterService

import com.typesafe.config.{ Config, ConfigFactory, ConfigParseOptions }
import com.typesafe.scalalogging.LazyLogging

import configs.syntax._

object FlintServer extends LazyLogging {
  def main(args: Array[String]): Unit = {
    import FlintCtx.owner

    val configParseOptions = ConfigParseOptions.defaults.setAllowMissing(false)
    val config             = ConfigFactory.defaultApplication(configParseOptions)
    validateConfig(config)

    val flintConfig  = config.get[Config]("flint").value
    val serverConfig = flintConfig.get[Config]("server").value
    val bindAddress  = serverConfig.get[String]("bind_address").value

    val bindInterface = bindAddress.takeWhile(_ != ':')
    val bindPort      = bindAddress.dropWhile(_ != ':').drop(1).toInt

    val serviceRoute = "/api/version/1/messaging"

    val clusterService =
      serverConfig.get[String]("cluster_service").value match {
        case "aws"  => new AwsClusterService(flintConfig)
        case "mock" => new MockClusterService
      }
    logger.info("Using " + clusterService.getClass.getSimpleName)

    val server: Server with Killable = AkkaServer(clusterService)
    val bindingFuture                = server.bindTo(bindInterface, bindPort, serviceRoute)

    bindingFuture.map { binding =>
      logger.info(s"Flint messaging server online at ws://$bindAddress$serviceRoute")
      // scalastyle:off println
      println("Press RETURN to shut down")
      // scalastyle:on println
      scala.io.StdIn.readLine

      binding
    }.flatMap(_.unbind).onComplete(_ => server.terminate)
  }

  private def validateConfig(config: Config): Unit = {
    validateFlintConfig(config)
    config.checkValid(ConfigFactory.defaultReference, "flint.server")
  }
}
