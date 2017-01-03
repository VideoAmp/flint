package flint

import java.time.{ Duration, Instant }

import rx._

case class Cluster(
    id: ClusterId,
    dockerImage: Rx[DockerImage],
    owner: String,
    ttl: Option[Duration],
    idleTimeout: Option[Duration],
    master: Instance,
    workers: Rx[Seq[Instance]],
    launchedAt: Instant)(implicit ctx: Ctx.Owner) {
  import Cluster.mergeInstanceStates

  val instances: Rx[Seq[Instance]]      = Rx { master +: workers() }
  val runningWorkers: Rx[Seq[Instance]] = Rx { workers().filter(_.instanceState() == Running) }
  val unterminatedWorkers: Rx[Seq[Instance]] =
    Rx { workers().filterNot(_.instanceState() == Terminated) }

  val cores: Rx[Int]              = Rx { runningWorkers().map(_.specs.cores).sum }
  val memory: Rx[Space]           = Rx { runningWorkers().map(_.specs.memory).sum }
  val storage: Rx[Space]          = Rx { runningWorkers().map(_.specs.storage.totalStorage).sum }
  val hourlyPrice: Rx[BigDecimal] = Rx { unterminatedWorkers().map(_.specs.hourlyPrice).sum }

  val state = Rx { instances().map(_.instanceState()).reduce(mergeInstanceStates) }

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
      workers: Seq[Instance],
      launchedAt: Instant)(implicit ctx: Ctx.Owner): Cluster =
    new Cluster(id, Var(dockerImage), owner, ttl, idleTimeout, master, Var(workers), launchedAt)

  def mergeInstanceStates(state1: LifecycleState, state2: LifecycleState): LifecycleState =
    (state1, state2) match {
      case (Running, x)       => x
      case (x, Running)       => x
      case (Starting, x)      => x
      case (x, Starting)      => x
      case (Terminated, x)    => x
      case (x, Terminated)    => x
      case (Terminating, x)   => x
      case (x, Terminating)   => x
      case (Pending, Pending) => Pending
    }
}
