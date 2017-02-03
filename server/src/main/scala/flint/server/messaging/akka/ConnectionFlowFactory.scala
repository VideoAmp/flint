package flint
package server
package messaging
package akka

import scala.concurrent.Promise

import _root_.akka.{ Done, NotUsed }
import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.model.ws.{ Message => WsMessage, _ }
import _root_.akka.stream._
import _root_.akka.stream.scaladsl._

import com.typesafe.scalalogging.LazyLogging

import rx._

private[akka] class ConnectionFlowFactory(
    messageSender: MessageSender[ServerMessage],
    messageReceiver: MessageReceiver)(
    implicit ctx: Ctx.Owner,
    actorSystem: ActorSystem,
    materializer: Materializer)
    extends LazyLogging {
  def newConnectionFlow()(
      implicit ctx: Ctx.Owner,
      materializer: Materializer): Flow[WsMessage, WsMessage, NotUsed] = {
    val completionPromise = Promise[Done]
    val completionSink = Sink.onComplete[Message] { message =>
      logger.trace(s"Completing completion promise $completionPromise with " + message)
      try {
        completionPromise.complete(message)
      } catch {
        case ex: Exception =>
          logger.error(s"Caught exception completing completion promise $completionPromise", ex)
      }
    }
    val sink =
      Flow[WsMessage].collect {
        case message: TextMessage =>
          logger.trace("Received message " + message.text)
          message.text
      }.map(
          messageText =>
            MessageCodec
              .decode(messageText)
              .leftMap(logDecodingErrors(logger, messageText, _))
              .toOption
              .collect {
                case message: ClientMessage =>
                  messageReceiver.receivedMessage.asVar() = Some(message)
                  message
                case message: ServerMessage =>
                  messageSender.sendMessage(message)
                  message
            })
        .collect {
          case Some(message) =>
            logger.trace("Processed message " + message)
            message
        }
        .to(completionSink)

    val source = new RxStreamSource(
      messageReceiver.receivedMessage.map(_.map(MessageCodec.encode)).map(_.map(TextMessage(_))))(
      completionPromise.future)

    Flow.fromSinkAndSource(sink, source).collect {
      case Some(message) =>
        logger.trace("Routing message " + message)
        message
    }
  }
}
