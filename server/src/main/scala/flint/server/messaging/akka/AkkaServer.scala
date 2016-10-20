package flint
package server
package messaging
package akka

import service.ClusterService

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future

import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.Http
import _root_.akka.http.scaladsl.model._, HttpMethods.GET
import _root_.akka.http.scaladsl.model.ws.{ Message => _, _ }
import _root_.akka.stream.{ Server => _, _ }
import _root_.akka.stream.scaladsl._

import com.typesafe.scalalogging.LazyLogging

import rx._

class AkkaServer(clusterService: ClusterService)(
    implicit ctx: Ctx.Owner,
    actorSystem: ActorSystem,
    materializer: Materializer)
    extends Server
    with LazyLogging {
  private val (messageSender, messageReceiver) = {
    val messageSource = Source.queue[TextMessage](0, OverflowStrategy.backpressure)
    val messageSink   = Sink.queue[TextMessage]
    val (messageSourceQueue, messageSinkQueue) =
      messageSource.toMat(messageSink)(Keep.both).run
    val sender =
      new AkkaWebSocketMessageSender[ServerMessage](messageSourceQueue, MessageCodec.encode)
    val receiver =
      new AkkaWebSocketMessageReceiver[ClientMessage](messageSinkQueue, MessageCodec.decode)

    (sender, receiver)
  }

  private val messageId = new AtomicInteger(0)

  messageReceiver.receivedMessage.foreach { optMessage =>
    optMessage.foreach { message =>
      logger.trace(s"Received $message")
    }
  }

  val result: Rx[Future[Option[ServerMessage]]] = Rx {
    messageReceiver.receivedMessage() match {
      case Some(AddWorkers(clusterId, count)) =>
        clusterService.clusters.now
          .get(clusterId)
          .map(_.addWorkers(count))
          .map { optAddWorkers =>
            optAddWorkers.map(_ => None).recover {
              case ex =>
                val error = s"Failed to add workers to cluster with id: $clusterId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Add workers: no cluster with id: $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error => sendMessage(WorkerAdditionAttempt(_, clusterId, count, error)))
          .map(Some(_))
      case Some(ChangeDockerImage(clusterId, dockerImage)) =>
        clusterService.clusters.now
          .get(clusterId)
          .map(_.changeDockerImage(dockerImage))
          .map { optChangeDockerImage =>
            optChangeDockerImage.map(_ => None).recover {
              case ex =>
                val error = s"Failed to change Docker image of cluster with id: $clusterId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Change docker image: no cluster with id: $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(DockerImageChangeAttempt(_, clusterId, dockerImage, error)))
          .map(Some(_))
      case Some(LaunchCluster(clusterSpec)) =>
        clusterService
          .launchCluster(clusterSpec)
          .map(_ => None)
          .recover {
            case ex =>
              val error = s"Failed to launch cluster for spec: $clusterSpec"
              logger.error(error, ex)
              Some(error)
          }
          .flatMap(error => sendMessage(ClusterLaunchAttempt(_, clusterSpec, error)))
          .map(Some(_))
      case Some(TerminateCluster(clusterId)) =>
        clusterService.clusters.now
          .get(clusterId)
          .map(_.terminate)
          .map { optTermination =>
            optTermination.map(_ => None).recover {
              case ex =>
                val error = s"Failed to terminate cluster with id: $clusterId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Terminate cluster: no cluster with id: $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(ClusterTerminationAttempt(_, clusterId, ClientRequested, error)))
          .map(Some(_))
      case Some(TerminateWorker(instanceId)) =>
        clusterService.clusters.now.values
          .flatMap(_.cluster.liveWorkers.now)
          .find(_.id == instanceId)
          .map(_.terminate)
          .map { optTermination =>
            optTermination.map(_ => None).recover {
              case ex =>
                val error = s"Failed to terminate worker with instance id: $instanceId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Terminate worker: no worker with instance id: $instanceId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(WorkerTerminationAttempt(_, instanceId, ClientRequested, error)))
          .map(Some(_))
      case Some(clientMessage) =>
        logger.error(s"Don't know how to handle client message: $clientMessage")
        Future.successful(None)
      case None => Future.successful(None)
    }
  }

  result.foreach { futureOptMessage =>
    futureOptMessage.foreach { optMessage =>
      optMessage.foreach { message =>
        logger.trace(s"Replied with $message")
      }
    }
  }

  private val connectionFlowFactory = new ConnectionFlowFactory(messageSender, messageReceiver)

  override def bindTo(interface: String, port: Int, path: String): Future[Binding] = {
    val requestHandler: HttpRequest => HttpResponse = {
      case req @ HttpRequest(GET, Uri.Path(`path`), _, _, _) =>
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) =>
            upgrade.handleMessages(connectionFlowFactory.newConnectionFlow)
          case None =>
            HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case req =>
        req.discardEntityBytes()
        HttpResponse(404)
    }

    Http().bindAndHandleSync(requestHandler, interface, port).map { serverBinding =>
      new Binding {
        override def unbind(): Future[Unit] = serverBinding.unbind
      }
    }
  }

  private def sendMessage(f: Int => ServerMessage): Future[ServerMessage] =
    messageSender.synchronized {
      val message = f(messageId.incrementAndGet)
      messageSender.sendMessage(message)
    }
}

object AkkaServer {
  def apply(clusterService: ClusterService)(implicit ctx: Ctx.Owner): AkkaServer with Killable = {
    implicit val actorSystem =
      ActorSystem("default", defaultExecutionContext = Some(flintExecutionContext))
    implicit val materializer = ActorMaterializer()

    new AkkaServer(clusterService) with Killable {
      override def terminate(): Future[Unit] = actorSystem.terminate.map(_ => ())
    }
  }
}
