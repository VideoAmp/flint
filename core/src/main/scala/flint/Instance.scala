package flint

import InstanceState._

import java.net.InetAddress
import java.time.Instant

import scala.concurrent.Future
import scala.util.Try

import rx._

case class Instance(
    id: String,
    ipAddress: Rx[Option[InetAddress]],
    subnet: Rx[Option[Subnet]],
    placementGroup: Option[String],
    dockerImage: Rx[Option[DockerImage]],
    state: Rx[InstanceState],
    containerState: Rx[ContainerState],
    specs: InstanceSpecs,
    launchedAt: Instant,
    terminatedAt: Rx[Option[Instant]])(terminator: Instance => Future[Unit])(
    implicit ctx: Ctx.Owner)
    extends Killable {

  override def terminate(): Future[Unit] =
    Future
      .fromTry(Try {
        require(state.now != Terminating, "instance is already terminating")
        require(state.now != Terminated, "instance is already terminated")
      })
      .flatMap(_ => terminator(this))

  val effectiveContainerState: Rx[ContainerState] = Rx {
    containerState().constrainedBy(state())
  }

  override def equals(other: Any): Boolean = other match {
    case otherInstance: Instance => id == otherInstance.id
    case _                       => false
  }

  override def hashCode(): Int = id.hashCode
}

object Instance {
  private[flint] def apply(
      id: String,
      ipAddress: Option[InetAddress],
      subnet: Option[Subnet],
      placementGroup: Option[String],
      dockerImage: Option[DockerImage],
      state: InstanceState,
      containerState: ContainerState,
      specs: InstanceSpecs,
      launchedAt: Instant,
      terminatedAt: Option[Instant])(terminator: Instance => Future[Unit])(
      implicit ctx: Ctx.Owner): Instance =
    new Instance(
      id,
      Var(ipAddress),
      Var(subnet),
      placementGroup,
      Var(dockerImage),
      Var(state),
      Var(containerState),
      specs,
      launchedAt,
      Var(terminatedAt))(terminator)
}
