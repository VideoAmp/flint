package flint
package server
package messaging

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import _root_.akka.http.scaladsl.model.ws.TextMessage
import _root_.akka.stream.{ Materializer, QueueOfferResult }
import _root_.akka.stream.scaladsl.SourceQueue

package object akka {
  implicit class SyncOfferSourceQueue[T](sourceQueue: SourceQueue[T]) {
    def syncOffer(elem: T, timeout: Duration = Duration.Inf): QueueOfferResult =
      sourceQueue.synchronized {
        Await.result(sourceQueue.offer(elem), timeout)
      }
  }

  implicit class DecodableTextMessage(textMessage: TextMessage) {
    def text(implicit materializer: Materializer): String =
      textMessage match {
        case TextMessage.Strict(messageText) =>
          messageText
        case TextMessage.Streamed(textStream) =>
          Await.result(textStream.runFold("")(_ + _), Duration.Inf)
      }
  }
}
