package flint
package server
package messaging
package akka

import service.ClusterService
import service.docker.{ Credentials, Tags }

import java.net.URI

import scala.concurrent.Future

import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.Http
import _root_.akka.http.scaladsl.model._, HttpMethods.GET
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

  private val httpClient = HttpClientBuilder.create.build
  private val dockerTags = new Tags(httpClient)

  private val (messageSender, messageReceiver) = {
    val messageSource = Source.queue[TextMessage](0, OverflowStrategy.backpressure)
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
    new MessagingProtocol(clusterService, messageSender, messageReceiver)

  private val connectionFlowFactory = new ConnectionFlowFactory(messageSender, messageReceiver)

  override def bindTo(interface: String, port: Int, apiRoot: String): Future[Binding] = {
    val messagingPath     = apiRoot + "/messaging"
    val clustersPath      = apiRoot + "/clusters"
    val dockerImagesPath  = apiRoot + "/dockerImages"
    val instanceSpecsPath = apiRoot + "/instanceSpecs"
    val requestHandler: HttpRequest => HttpResponse = {
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
        val clusters         = clusterService.clusterSystem.clusters.now.values.map(_.cluster)
        val clusterSnapshots = clusters.map(ClusterSnapshot(_)).toList
        val responseBody     = compactJson(toJValue(clusterSnapshots))
        HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseBody))
      case req @ HttpRequest(GET, Uri.Path(`dockerImagesPath`), _, _, _) =>
        logger.info("Received GET request for docker images")
        dockerTags(dockerImageRepo, Some(dockerCreds)) match {
          case Success(dockerImages) =>
            val responseBody = compactJson(toJValue(dockerImages))
            HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseBody))
          case Failure(errors) =>
            val responseBody = compactJson(toJValue(errors))
            HttpResponse(400, entity = HttpEntity(ContentTypes.`application/json`, responseBody))
        }
      case req @ HttpRequest(GET, Uri.Path(`instanceSpecsPath`), _, _, _) =>
        val responseBody = compactJson(toJValue(clusterService.instanceSpecs.toList))
        HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, responseBody))
      case req =>
        req.discardEntityBytes()
        HttpResponse(404)
    }

    Http().bindAndHandleSync(requestHandler, interface, port).map { serverBinding =>
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
      implicit ctx: Ctx.Owner): AkkaServer with Killable = {
    implicit val actorSystem =
      ActorSystem("default", defaultExecutionContext = Some(flintExecutionContext))
    implicit val materializer = ActorMaterializer()

    new AkkaServer(clusterService, dockerImageRepo, dockerCreds) with Killable {
      override def terminate(): Future[Unit] = actorSystem.terminate.map(_ => ())
    }
  }
}
