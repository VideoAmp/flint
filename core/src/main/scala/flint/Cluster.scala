package flint

import java.time.Instant

import scala.concurrent.duration.FiniteDuration

import rx._

case class Cluster(
    id: ClusterId,
    dockerImage: Rx[DockerImage],
    owner: String,
    ttl: Option[FiniteDuration],
    idleTimeout: Option[FiniteDuration],
    master: Instance,
    workers: Rx[Seq[Instance]],
    launchedAt: Instant)(implicit ctx: Ctx.Owner) {
  import Cluster.{ mergeContainerStates, mergeInstanceStates }

  val instances: Rx[Seq[Instance]]      = Rx { master +: workers() }
  val runningWorkers: Rx[Seq[Instance]] = Rx { workers().filter(_.state() == Running) }
  val unterminatedWorkers: Rx[Seq[Instance]] =
    Rx { workers().filterNot(_.state() == Terminated) }

  val cores: Rx[Int]              = Rx { runningWorkers().map(_.specs.cores).sum }
  val memory: Rx[Space]           = Rx { runningWorkers().map(_.specs.memory).sum }
  val storage: Rx[Space]          = Rx { runningWorkers().map(_.specs.storage.totalStorage).sum }
  val hourlyPrice: Rx[BigDecimal] = Rx { unterminatedWorkers().map(_.specs.hourlyPrice).sum }

  val state          = Rx { instances().map(_.state()).reduce(mergeInstanceStates) }
  val containerState = Rx { instances().map(_.containerState()).reduce(mergeContainerStates) }

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
      ttl: Option[FiniteDuration],
      idleTimeout: Option[FiniteDuration],
      master: Instance,
      workers: Seq[Instance],
      launchedAt: Instant)(implicit ctx: Ctx.Owner): Cluster =
    new Cluster(id, Var(dockerImage), owner, ttl, idleTimeout, master, Var(workers), launchedAt)

  def mergeContainerStates(state1: ContainerState, state2: ContainerState): ContainerState =
    (state1, state2) match {
      case (ContainerRunning, x)                => x
      case (x, ContainerRunning)                => x
      case (ContainerStarting, x)               => x
      case (x, ContainerStarting)               => x
      case (ContainerStopped, x)                => x
      case (x, ContainerStopped)                => x
      case (ContainerStopping, x)               => x
      case (x, ContainerStopping)               => x
      case (ContainerPending, ContainerPending) => ContainerPending
    }

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
