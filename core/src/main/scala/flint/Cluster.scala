package flint

import java.time.Duration
import java.util.UUID

import scala.concurrent.Future

import rx.{ Ctx, Rx }

case class Cluster(
    id: UUID,
    dockerImage: Rx[DockerImage],
    owner: String,
    ttl: Option[Duration],
    idleTimeout: Option[Duration],
    master: SparkMaster,
    workers: Rx[Seq[Instance]])(terminator: () => Future[Unit])(implicit ctx: Ctx.Owner)
    extends Lifecycle {
  val cores: Rx[Int]              = Rx { workers().map(_.specs.cores).sum }
  val memory: Rx[Int]             = Rx { workers().map(_.specs.memory).sum }
  val hourlyPrice: Rx[BigDecimal] = Rx { workers().map(_.specs.hourlyPrice).sum }

  override val lifecycleState =
    Rx {
      (master.instance +: workers()).map(_.lifecycleState()).reduce(LifecycleState.reduce)
    }

  // TODO
  def addWorkers(count: Int): Future[Seq[Instance]] = {
    require(lifecycleState.now == Running, "Cluster must be running to add workers")
    ???
  }

  // TODO
  def bootDockerImage(dockerImage: DockerImage): Future[Unit] = {
    require(lifecycleState.now == Running, "Cluster must be running to boot a docker image")
    ???
  }

  override def terminate(): Future[Unit] = terminator()

  override def equals(other: Any): Boolean = other match {
    case otherCluster: Cluster => id == otherCluster.id
    case _                     => false
  }

  override def hashCode(): Int = id.hashCode
}
