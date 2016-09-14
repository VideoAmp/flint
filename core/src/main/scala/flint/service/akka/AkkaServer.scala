package flint
package service
package akka

import server.{ Binding, Server }

import scala.concurrent.Future

import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.Http
import _root_.akka.http.scaladsl.model._, HttpMethods.GET
import _root_.akka.http.scaladsl.model.ws.{ Message => _, _ }
import _root_.akka.stream.{ Server => _, _ }
import _root_.akka.stream.scaladsl._

import rx._

class AkkaServer()(implicit ctx: Ctx.Owner, actorSystem: ActorSystem, materializer: Materializer)
    extends Server {
  private val (messageSender, messageReceiver) = {
    val messageSource = Source.queue[TextMessage](0, OverflowStrategy.backpressure)
    val messageSink   = Sink.queue[TextMessage]
    val (messageSourceQueue, messageSinkQueue) =
      messageSource.toMat(messageSink)(Keep.both).run

    (new AkkaWebSocketMessageSender[ServerMessage](messageSourceQueue),
     new AkkaWebSocketMessageReceiver[ClientMessage](messageSinkQueue))
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
}

object AkkaServer {
  def apply()(implicit ctx: Ctx.Owner): AkkaServer with Killable = {
    implicit val actorSystem =
      ActorSystem("default", defaultExecutionContext = Some(flintExecutionContext))
    implicit val materializer = ActorMaterializer()

    new AkkaServer with Killable {
      override def terminate(): Future[Unit] = actorSystem.terminate.map(_ => ())
    }
  }
}
