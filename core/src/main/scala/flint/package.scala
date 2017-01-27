import java.util.UUID
import java.util.concurrent.{ Executors, ScheduledExecutorService, ThreadFactory }
import java.util.concurrent.atomic.AtomicInteger

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
    def collectFirst[U](pf: PartialFunction[T, U])(
        implicit ctx: Ctx.Owner,
        executor: ExecutionContext): Future[U] = {
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
      }(executor)
    }
  }

  def validateFlintConfig(config: Config): Unit = {
    val referenceConfig = ConfigFactory.defaultReference
    config.checkValid(referenceConfig, "aws")
    config.checkValid(referenceConfig, "docker")
  }

  private[flint] def flintThreadFactory(poolName: String) =
    new ThreadFactory {
      private val threadNumber   = new AtomicInteger
      private val defaultFactory = Executors.defaultThreadFactory

      override def newThread(r: Runnable) = {
        val thread = defaultFactory.newThread(r)
        thread.setName("flint-" + poolName + "-" + threadNumber.incrementAndGet)
        thread.setDaemon(true)
        thread
      }
    }

  /**
    * The common Flint thread pool for IO operations
    */
  lazy val ioExecutorService: ScheduledExecutorService =
    Executors.newScheduledThreadPool(
      Runtime.getRuntime.availableProcessors * 2,
      flintThreadFactory("io-thread"))

  /**
    * An [[ExecutionContextExecutorService]] that wraps [[ioExecutorService]]
    */
  lazy val ioExecutionContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(ioExecutorService)

  /**
    * The single-threaded Flint update execution context. Perform any Flint model updates within
    * this context. DO NOT BLOCK IN THIS CONTEXT!
    */
  lazy val updateExecutionContext: ExecutionContextExecutorService = {
    val executorService = Executors.newSingleThreadExecutor(flintThreadFactory("update-thread"))
    ExecutionContext.fromExecutorService(executorService)
  }

  /**
    * We make this private[flint] to ensure that library users do not accidentally and unwittingly
    * import it as their implicit execution context
    */
  private[flint] final implicit lazy val implicitExecutionContext: ExecutionContext =
    updateExecutionContext

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
