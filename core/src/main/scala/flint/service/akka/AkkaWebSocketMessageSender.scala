package flint
package service
package akka

import scala.concurrent.Future

import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.model.ws.{ Message => _, _ }
import _root_.akka.stream.QueueOfferResult
import _root_.akka.stream.scaladsl._

private[akka] class AkkaWebSocketMessageSender[Send <: Message](
    sourceQueue: SourceQueue[TextMessage])(implicit actorSystem: ActorSystem)
    extends MessageSender[Send] {
  import actorSystem.dispatcher

  def sendMessage(message: Send): Future[Unit] = {
    import MessageSerializer.serializeMessage

    val messageText = serializeMessage(message)
    val wsMessage   = TextMessage(messageText)

    sourceQueue.synchronized {
      sourceQueue.offer(wsMessage)
    } flatMap {
      case QueueOfferResult.Enqueued    => Future.successful(())
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.Dropped =>
        Future.failed(
          new RuntimeException(
            "Dropped message when attempting to enqueue: " + message.getClass.getSimpleName))
      case QueueOfferResult.QueueClosed =>
        Future.failed(new RuntimeException(
          "Attempted to enqueue message for closed connection: " + message.getClass.getSimpleName))
    }
  }
}
