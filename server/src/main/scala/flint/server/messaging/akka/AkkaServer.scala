package flint
package server
package messaging
package akka

import docker.{ Credentials, Tags }
import service.ClusterService

import java.net.URI

import scala.concurrent.Future
import scala.util.Random

import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.Http
import _root_.akka.http.scaladsl.model._, HttpMethods.GET
import _root_.akka.http.scaladsl.model.headers.`Access-Control-Allow-Origin`
import _root_.akka.http.scaladsl.model.ws.{ Message => _, _ }
import _root_.akka.stream.{ Server => _, _ }
import _root_.akka.stream.scaladsl._

import com.typesafe.scalalogging.LazyLogging

import io.sphere.json._

import org.apache.http.impl.client.HttpClientBuilder
import org.json4s._, jackson._

import rx._

import scalaz.{ Failure, Success }

class AkkaServer(
    clusterService: ClusterService,
    dockerImageRepo: String,
    dockerCreds: Credentials)(
    implicit ctx: Ctx.Owner,
    actorSystem: ActorSystem,
    materializer: Materializer)
    extends Server
    with LazyLogging {
  import actorSystem.dispatcher

  private val serverId = "%8h".format(Random.nextInt)

  private val httpClient = HttpClientBuilder.create.build
  private val dockerTags = new Tags(httpClient)

  private val (messageSender, messageReceiver) = {
    val messageSource = Source.queue[TextMessage](100, OverflowStrategy.dropBuffer)
    val messageSink   = Sink.queue[TextMessage]
    val (messageSourceQueue, messageSinkQueue) =
      messageSource.toMat(messageSink)(Keep.both).run
    val sender =
      new AkkaWebSocketMessageSender[ServerMessage](messageSourceQueue, MessageCodec.encode)
    val receiver =
      new AkkaWebSocketMessageReceiver(messageSinkQueue, MessageCodec.decode)

    (sender, receiver)
  }

  private val protocol =
    new MessagingProtocol(serverId, clusterService, messageSender, messageReceiver)

  private val connectionFlowFactory = new ConnectionFlowFactory(messageSender, messageReceiver)

  override def bindTo(interface: String, port: Int, apiRoot: String): Future[Binding] = {
    val messagingPath     = apiRoot + "/messaging"
    val clustersPath      = apiRoot + "/clusters"
    val dockerImagesPath  = apiRoot + "/dockerImages"
    val instanceSpecsPath = apiRoot + "/instanceSpecs"
    val spotPricesPath    = apiRoot + "/spotPrices"

    val syncRequestHandler: PartialFunction[HttpRequest, HttpResponse] = {
      case req @ HttpRequest(GET, Uri.Path(`messagingPath`), _, _, _) =>
        logger.info("Received GET request for messaging websocket")
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) =>
            upgrade.handleMessages(connectionFlowFactory.newConnectionFlow)
          case None =>
            HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case req @ HttpRequest(GET, Uri.Path(`clustersPath`), _, _, _) =>
        logger.info("Received GET request for clusters")
        val managedClusters  = clusterService.clusterSystem.clusters.now.values
        val clusterSnapshots = managedClusters.map(ClusterSnapshot(_)).toList
        val responseBody     = compactJson(toJValue(clusterSnapshots))
        HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseBody))
          .withHeaders(`Access-Control-Allow-Origin`.*)
      case req @ HttpRequest(GET, Uri.Path(`dockerImagesPath`), _, _, _) =>
        logger.info("Received GET request for docker images")
        dockerTags(dockerImageRepo, Some(dockerCreds)) match {
          case Success(dockerImages) =>
            val responseBody = compactJson(toJValue(dockerImages))
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseBody))
              .withHeaders(`Access-Control-Allow-Origin`.*)
          case Failure(errors) =>
            val responseBody = compactJson(toJValue(errors))
            HttpResponse(400, entity = HttpEntity(ContentTypes.`application/json`, responseBody))
              .withHeaders(`Access-Control-Allow-Origin`.*)
        }
      case req @ HttpRequest(GET, Uri.Path(`instanceSpecsPath`), _, _, _) =>
        logger.info("Received GET request for instance specs")
        val responseBody = compactJson(toJValue(clusterService.instanceSpecs.toList))
        HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseBody))
          .withHeaders(`Access-Control-Allow-Origin`.*)
    }
    val asyncRequestHandler: PartialFunction[HttpRequest, Future[HttpResponse]] = {
      case req @ HttpRequest(GET, uri @ Uri.Path(`spotPricesPath`), _, _, _) =>
        uri
          .query()
          .get("instanceTypes")
          .map { rawInstanceTypes =>
            val instanceTypes = rawInstanceTypes.split(",", -1)
            logger.info(
              "Received GET request for spot prices for instance types: " + instanceTypes
                .mkString(", "))
            clusterService.getSpotPrices(instanceTypes: _*).map { spotPrices =>
              val responseBody = compactJson(toJValue(spotPrices.toList))
              HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseBody))
                .withHeaders(`Access-Control-Allow-Origin`.*)
            }
          }
          .getOrElse {
            logger.info("Received GET request for spot prices without specifying instance types")
            Future.successful(
              HttpResponse(400)
                .withHeaders(`Access-Control-Allow-Origin`.*)
                .withEntity("No instance types specified\n"))
          }
    }
    val notFoundHandler: PartialFunction[HttpRequest, Future[HttpResponse]] = {
      case req @ HttpRequest(_, Uri.Path(requestPath), _, _, _) =>
        logger.info("Received request for unknown path " + requestPath)
        req.discardEntityBytes()
        Future.successful(HttpResponse(404).withHeaders(`Access-Control-Allow-Origin`.*))
    }
    val requestHandler: HttpRequest => Future[HttpResponse] =
      syncRequestHandler
        .andThen(Future.successful)
        .orElse(asyncRequestHandler)
        .orElse(notFoundHandler)

    Http().bindAndHandleAsync(requestHandler, interface, port).map { serverBinding =>
      new Binding {
        private val bindAddress = interface + ":" + port

        override val messagingUrl: URI = new URI("ws://" + bindAddress + messagingPath)

        override val serviceUrl: URI = new URI("http://" + bindAddress + apiRoot)

        override def unbind(): Future[Unit] = serverBinding.unbind
      }
    }
  }
}

object AkkaServer {
  def apply(clusterService: ClusterService, dockerImageRepo: String, dockerCreds: Credentials)(
      implicit ctx: Ctx.Owner,
      actorSystem: ActorSystem,
      materializer: Materializer): AkkaServer with Killable =
    new AkkaServer(clusterService, dockerImageRepo, dockerCreds) with Killable {
      override def terminate(): Future[Unit] =
        actorSystem.terminate.map(_ => ())(actorSystem.dispatcher)
    }
}
