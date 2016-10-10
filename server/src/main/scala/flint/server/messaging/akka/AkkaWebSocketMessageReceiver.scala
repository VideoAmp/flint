package flint
package server
package messaging
package akka

import scala.util.{ Failure, Success }

import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.model.ws.{ Message => _, _ }
import _root_.akka.stream.Materializer
import _root_.akka.stream.scaladsl._

import com.typesafe.scalalogging.LazyLogging

import rx._

private[akka] class AkkaWebSocketMessageReceiver[Recv <: Message](
    messageSinkQueue: SinkQueue[TextMessage])(
    implicit actorSystem: ActorSystem,
    materializer: Materializer)
    extends MessageReceiver[Recv]
    with LazyLogging {
  import actorSystem.dispatcher

  import MessageSerializer.deserializeMessage

  override val receivedMessage = Var[Option[Recv]](None)

  loop(messageSinkQueue.pull) {
    case Success(Some(TextMessage.Strict(messageText))) =>
      receivedMessage() = Some(deserializeMessage[Recv](messageText))
    case Success(Some(TextMessage.Streamed(textStream))) =>
      textStream
        .runFold("")(_ + _)
        .map(deserializeMessage[Recv])
        .map(Option(_))
        .foreach(receivedMessage() = _)
    case Success(None) =>
      logger.info("Message sink: no more messages")
    case Failure(ex) =>
      logger.error("Caught exception pulling from the message sink queue", ex)
  }
}
