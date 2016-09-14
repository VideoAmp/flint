package flint
package server

import service.akka.AkkaServer

import com.typesafe.config.{ Config, ConfigFactory, ConfigParseOptions }

import configs.syntax._

import rx._

object FlintServer {
  def main(args: Array[String]): Unit = {
    import Ctx.Owner.Unsafe._

    val configParseOptions = ConfigParseOptions.defaults.setAllowMissing(false)
    val config             = ConfigFactory.defaultApplication(configParseOptions)
    validateConfig(config)

    val serverConfig = config.get[Config]("flint.server").value
    val bindAddress  = serverConfig.get[String]("bind_address").value
    val serviceRoute = serverConfig.get[String]("service_route").value

    val bindInterface = bindAddress.takeWhile(_ != ':')
    val bindPort      = bindAddress.dropWhile(_ != ':').drop(1).toInt

    val server: Server with Killable = AkkaServer()
    val bindingFuture                = server.bindTo(bindInterface, bindPort, serviceRoute)

    bindingFuture.map { binding =>
      // scalastyle:off println
      println(s"Websocket server online at http://$bindAddress$serviceRoute")
      println("Press RETURN to stop...")
      // scalastyle:on println
      io.StdIn.readLine

      binding
    }.flatMap(_.unbind).onComplete(_ => server.terminate)
  }

  private def validateConfig(config: Config): Unit = {
    validateFlintConfig(config)
    config.checkValid(ConfigFactory.defaultReference, "flint.server")
  }
}
