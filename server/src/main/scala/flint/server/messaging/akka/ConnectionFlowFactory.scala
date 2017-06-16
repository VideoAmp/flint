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

private[akka] class ConnectionFlowFactory(messageReceiver: MessageReceiver)(
    implicit actorSystem: ActorSystem)
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
    val sink: Sink[WsMessage, NotUsed] =
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
                  logger.trace(s"Decoded message " + message)
                  messageReceiver.receivedMessage.asVar() = Some(message)
                  message
            })
        .collect {
          case Some(message) =>
            logger.trace("Processed message " + message)
            message
        }
        .to(completionSink)

    val source: Graph[SourceShape[Option[Message]], NotUsed] =
      new RxStreamSource(messageReceiver.receivedMessage)(completionPromise.future)

    Flow.fromSinkAndSource(sink, source).collect {
      case Some(message: ServerMessage) =>
        logger.trace("Routing message " + message)
        TextMessage(MessageCodec.encode(message))
    }
  }
}
