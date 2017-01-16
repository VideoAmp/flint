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

private[akka] class AkkaWebSocketMessageReceiver(
    messageSinkQueue: SinkQueue[TextMessage],
    decodeMessage: String => MessageValidation)(
    implicit actorSystem: ActorSystem,
    materializer: Materializer)
    extends MessageReceiver
    with LazyLogging {
  import actorSystem.dispatcher

  override val receivedMessage = Var(Option.empty[Message])

  loop(messageSinkQueue.pull) {
    case Success(Some(textMessage)) =>
      handleMessageText(textMessage.text)
    case Success(None) =>
      logger.info("Message sink: no more messages")
    case Failure(ex) =>
      logger.error("Caught exception pulling from the message sink queue", ex)
  }

  private def handleMessageText(messageText: String): Unit =
    decodeMessage(messageText).fold(
      errs => logDecodingErrors(logger, messageText, errs),
      message => receivedMessage() = Some(message)
    )
}
