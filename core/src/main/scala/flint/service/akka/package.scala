package flint
package service

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import _root_.akka.stream.QueueOfferResult
import _root_.akka.stream.scaladsl.SourceQueue

package object akka {
  implicit class RichSourceQueue[T](sourceQueue: SourceQueue[T]) {
    def syncOffer(elem: T, timeout: Duration = Duration.Inf): QueueOfferResult =
      sourceQueue.synchronized {
        Await.result(sourceQueue.offer(elem), timeout)
      }
  }
}
