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

import scalaz.Success

private[akka] class ConnectionFlowFactory(
    messageSender: MessageSender[ServerMessage],
    messageReceiver: MessageReceiver[ClientMessage])(
    implicit ctx: Ctx.Owner,
    actorSystem: ActorSystem,
    materializer: Materializer)
    extends LazyLogging {
  def newConnectionFlow()(
      implicit ctx: Ctx.Owner,
      materializer: Materializer): Flow[WsMessage, WsMessage, NotUsed] = {
    val completionPromise = Promise[Done]
    val completionSink    = Sink.onComplete[Message](completionPromise.complete)
    val sink =
      Flow[WsMessage].collect {
        case message: TextMessage => message.text
      }.map(
          messageText =>
            MessageCodec
              .decode[ServerMessage](messageText)
              .leftMap(logDecodingErrors(logger, messageText, _))
              .map { message =>
                messageSender.sendMessage(message)
                message
            })
        .collect { case Success(message) => message }
        .to(completionSink)

    val source = new RxStreamSource(
      messageReceiver.receivedMessage
        .filter(_.isDefined)
        .map(_.get)
        .map(MessageCodec.encode)
        .map(TextMessage(_)))(completionPromise.future)

    Flow.fromSinkAndSource(sink, source)
  }
}
