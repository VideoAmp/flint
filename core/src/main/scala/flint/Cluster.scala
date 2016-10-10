package flint

import java.time.Duration

import rx._

case class Cluster(
    id: ClusterId,
    dockerImage: Rx[DockerImage],
    owner: String,
    ttl: Option[Duration],
    idleTimeout: Option[Duration],
    master: Instance,
    workers: Rx[Seq[Instance]])(implicit ctx: Ctx.Owner)
    extends Lifecycle {
  val instances: Rx[Seq[Instance]]      = Rx { master +: workers() }
  val runningWorkers: Rx[Seq[Instance]] = Rx { workers().filter(_.lifecycleState() == Running) }
  val liveWorkers: Rx[Seq[Instance]] = Rx {
    workers().filterNot(_.lifecycleState() == Terminated)
  }

  val cores: Rx[Int]              = Rx { runningWorkers().map(_.specs.cores).sum }
  val memory: Rx[Int]             = Rx { runningWorkers().map(_.specs.memory).sum }
  val hourlyPrice: Rx[BigDecimal] = Rx { liveWorkers().map(_.specs.hourlyPrice).sum }

  override val lifecycleState =
    Rx {
      instances().map(_.lifecycleState()).reduce(LifecycleState.reduce)
    }

  override def equals(other: Any): Boolean = other match {
    case otherCluster: Cluster => id == otherCluster.id
    case _                     => false
  }

  override def hashCode(): Int = id.hashCode
}

object Cluster {
  private[flint] def apply(
      id: ClusterId,
      dockerImage: DockerImage,
      owner: String,
      ttl: Option[Duration],
      idleTimeout: Option[Duration],
      master: Instance,
      workers: Seq[Instance])(implicit ctx: Ctx.Owner): Cluster =
    new Cluster(id, Var(dockerImage), owner, ttl, idleTimeout, master, Var(workers))
}
