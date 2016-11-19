import java.util.UUID
import java.util.concurrent.{ Executors, ForkJoinPool, ThreadFactory }

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutorService, Future, Promise }
import scala.io.Source
import scala.util.{ Failure, Success, Try }

import com.typesafe.config.{ Config, ConfigFactory }

import rx._

package object flint extends Collections {
  type ClusterId = UUID

  object ClusterId {
    def apply(): ClusterId = UUID.randomUUID

    def apply(uuidString: String): ClusterId = UUID.fromString(uuidString)
  }

  // Flint's ur-context
  object FlintCtx {
    implicit val owner = Ctx.Owner.safe
  }

  implicit class CollectibleRx[T](rx: Rx[T]) {
    def collectFirst[U](pf: PartialFunction[T, U])(implicit ctx: Ctx.Owner): Future[U] = {
      val promise = Promise[U]()
      val obs = rx.trigger {
        rx.toTry match {
          case Success(x) if pf isDefinedAt x =>
            Try(pf(x)) match {
              case Success(value) => promise.success(value)
              case Failure(ex)    => promise.failure(ex)
            }
          case Success(_)  =>
          case Failure(ex) => promise.failure(ex)
        }
      }
      promise.future.andThen {
        case _ => obs.kill
      }
    }
  }

  def validateFlintConfig(config: Config): Unit = {
    val referenceConfig = ConfigFactory.defaultReference
    config.checkValid(referenceConfig, "aws")
    config.checkValid(referenceConfig, "docker")
  }

  val flintThreadFactory = new ThreadFactory with ForkJoinPool.ForkJoinWorkerThreadFactory {
    private val defaultWorkerFactory = ForkJoinPool.defaultForkJoinWorkerThreadFactory
    private val defaultFactory       = Executors.defaultThreadFactory

    override def newThread(pool: ForkJoinPool) = {
      val thread = defaultWorkerFactory.newThread(pool)
      thread.setDaemon(true)
      thread
    }

    override def newThread(r: Runnable) = {
      val thread = defaultFactory.newThread(r)
      thread.setDaemon(true)
      thread
    }
  }

  /**
    * We use a custom exection context throughout Flint because the AWS ExecutorFactory requires an
    * ExecutorService, and neither the global Scala ExecutionContext nor the ActorSystem.dispatcher
    * provide an ExecutorService to share. Also, we use daemon threads in this ExecutionContext so
    * that they don't block JVM exit
    */
  private[flint] implicit lazy val executionContext: ExecutionContextExecutorService = {
    val executorService =
      new ForkJoinPool(Runtime.getRuntime.availableProcessors, flintThreadFactory, null, true)

    ExecutionContext.fromExecutorService(executorService)
  }

  lazy val flintExecutionContext = executionContext

  private[flint] def loop[T, U](future: => Future[T])(f: Try[T] => U): Unit =
    future.andThen(PartialFunction(f)).onComplete(_ => loop(future)(f))

  private[flint] def readTextResource(resourceName: String): String =
    Source
      .fromInputStream(
        Thread.currentThread.getContextClassLoader.getResourceAsStream(resourceName),
        "UTF-8")
      .getLines
      .mkString("\n")

  private[flint] implicit class MacroString(string: String) {
    def replaceMacro(macroName: String, macroReplacement: Any): String =
      string.replace(s"%$macroName%", macroReplacement.toString)
  }

  private[flint] implicit class VarRx[T](rx: Rx[T]) {
    def asVar: Var[T] = rx.asInstanceOf[Var[T]]
  }
}
