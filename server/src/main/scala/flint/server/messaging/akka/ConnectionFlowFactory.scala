package flint
package server
package messaging
package akka

import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration.Duration

import _root_.akka.{ Done, NotUsed }
import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.model.ws.{ Message => WsMessage, _ }
import _root_.akka.stream._
import _root_.akka.stream.scaladsl._

import rx._

private[akka] class ConnectionFlowFactory(
    messageSender: MessageSender[ServerMessage],
    messageReceiver: MessageReceiver[ClientMessage])(
    implicit ctx: Ctx.Owner,
    actorSystem: ActorSystem,
    materializer: Materializer) {
  import ConnectionFlowFactory._

  def newConnectionFlow()(
      implicit ctx: Ctx.Owner,
      materializer: Materializer): Flow[WsMessage, WsMessage, NotUsed] = {
    val completionPromise = Promise[Done]
    val completionSink    = Sink.onComplete[Message](completionPromise.complete)
    val sink = Flow[WsMessage].collect {
      case message: TextMessage => decodeTextMessage(message)
    }.map(MessageSerializer.deserializeMessage[ServerMessage])
      .map { message =>
        messageSender.sendMessage(message)
        message
      }
      .to(completionSink)

    val source = new RxStreamSource(
      messageReceiver.receivedMessage
        .filter(_.isDefined)
        .map(_.get)
        .map(MessageSerializer.serializeMessage)
        .map(TextMessage(_)))(completionPromise.future)

    Flow.fromSinkAndSource(sink, source)
  }
}

private[akka] object ConnectionFlowFactory {
  private def decodeTextMessage(message: TextMessage)(
      implicit materializer: Materializer): String =
    message match {
      case TextMessage.Strict(messageText) =>
        messageText
      case TextMessage.Streamed(textStream) =>
        Await.result(textStream.runFold("")(_ + _), Duration.Inf)
    }
}
