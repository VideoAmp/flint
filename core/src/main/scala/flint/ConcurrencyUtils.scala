package flint

import java.util.concurrent.{ ScheduledExecutorService, TimeUnit }

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

object ConcurrencyUtils {
  def loop[T, U](future: => Future[T])(f: Try[T] => U)(
      implicit executor: ExecutionContext): Unit =
    future
      .andThen(PartialFunction(f))(executor)
      .onComplete(_ => loop(future)(f)(executor))(executor)

  def retryFuture[T](retries: Int)(f: => Future[T])(
      implicit executor: ExecutionContext,
      scheduledExecutorService: ScheduledExecutorService): Future[T] =
    retryFuture(retries, retries)(f)(executor, scheduledExecutorService)

  def retryFuture[T](retries: Int, maxRetries: Int)(f: => Future[T])(
      implicit executor: ExecutionContext,
      scheduledExecutorService: ScheduledExecutorService): Future[T] = {
    require(
      retries <= maxRetries,
      s"Retries $retries must be less than or equal to maximum retries $maxRetries")
    f.recoverWith {
      case _ if retries > 0 =>
        // Computes delay as a power of 2 based on how many retries we've performed, starting with 1
        // when retries == maxRetries
        val delaySeconds = ((1 << (maxRetries - retries + 1)) / 2).toLong
        val promise      = Promise[T]
        scheduledExecutorService.schedule(
          new Runnable {
            override def run() =
              promise.completeWith(
                retryFuture(retries - 1, maxRetries)(f)(executor, scheduledExecutorService))
          },
          delaySeconds,
          TimeUnit.SECONDS
        )
        promise.future
    }(executor)
  }
}
