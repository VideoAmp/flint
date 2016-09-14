package flint
package service
package akka

import scala.concurrent.Future
import scala.util.{ Failure, Success }

import _root_.akka.Done
import _root_.akka.actor.ActorSystem
import _root_.akka.stream._
import _root_.akka.stream.stage._

import rx._

class RxStreamSource[T](rx: Rx[T])(
    completionCallback: Future[Done])(implicit ctx: Ctx.Owner, actorSystem: ActorSystem)
    extends GraphStage[SourceShape[T]] {
  import actorSystem.dispatcher

  private val out = Outlet[T]("rxSource.out")

  override def shape = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with OutHandler {
      var obs: Option[Obs] = None
      setHandler(out, this)

      override def preStart() = {
        obs = Option(rx.foreach(emit(out, _)))

        completionCallback.onComplete {
          case Success(Done) =>
            cleanup
            completeStage
          case Failure(ex) => failStage(ex)
        }
      }

      override def onDownstreamFinish() = {
        cleanup
        completeStage
      }

      override def onPull() = ()

      private def cleanup() = obs.foreach(_.kill)
    }

  override def toString = classOf[RxStreamSource[T]].getSimpleName
}
