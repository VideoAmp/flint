package flint

import InstanceState._

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import rx._

case class Cluster(
    id: ClusterId,
    name: String,
    dockerImage: Rx[DockerImage],
    ttl: Option[FiniteDuration],
    idleTimeout: Option[FiniteDuration],
    master: Instance,
    workers: Rx[Seq[Instance]],
    launchedAt: Instant)(implicit ctx: Ctx.Owner) {
  val instances: Rx[Seq[Instance]]      = Rx { master +: workers() }
  val runningWorkers: Rx[Seq[Instance]] = Rx { workers().filter(_.state() == Running) }
  val unterminatedWorkers: Rx[Seq[Instance]] =
    Rx { workers().filterNot(_.state() == Terminated) }

  val cores: Rx[Int]              = Rx { runningWorkers().map(_.specs.cores).sum }
  val memory: Rx[Information]     = Rx { runningWorkers().map(_.specs.memory).sum }
  val storage: Rx[Information]    = Rx { runningWorkers().map(_.specs.storage.totalStorage).sum }
  val hourlyPrice: Rx[BigDecimal] = Rx { unterminatedWorkers().map(_.specs.hourlyPrice).sum }

  override def equals(other: Any): Boolean = other match {
    case otherCluster: Cluster => id == otherCluster.id
    case _                     => false
  }

  override def hashCode(): Int = id.hashCode
}

object Cluster {
  private[flint] def apply(
      id: ClusterId,
      name: String,
      dockerImage: DockerImage,
      ttl: Option[FiniteDuration],
      idleTimeout: Option[FiniteDuration],
      master: Instance,
      workers: Seq[Instance],
      launchedAt: Instant)(implicit ctx: Ctx.Owner): Cluster =
    new Cluster(id, name, Var(dockerImage), ttl, idleTimeout, master, Var(workers), launchedAt)
}
