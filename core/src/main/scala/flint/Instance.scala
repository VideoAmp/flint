package flint

import java.net.InetAddress

import scala.concurrent.Future
import scala.util.Try

import rx._

case class Instance(
    id: String,
    ipAddress: InetAddress,
    placementGroup: Option[String],
    dockerImage: Rx[Option[DockerImage]],
    state: Rx[LifecycleState],
    containerState: Rx[ContainerState],
    specs: InstanceSpecs)(terminator: Instance => Future[Unit])
    extends Killable {

  override def terminate(): Future[Unit] =
    Future
      .fromTry(Try {
        require(state.now != Terminating, "instance is already terminating")
        require(state.now != Terminated, "instance is already terminated")
      })
      .flatMap(_ => terminator(this))

  override def equals(other: Any): Boolean = other match {
    case otherInstance: Instance => id == otherInstance.id
    case _                       => false
  }

  override def hashCode(): Int = id.hashCode
}

object Instance {
  private[flint] def apply(
      id: String,
      ipAddress: InetAddress,
      placementGroup: Option[String],
      dockerImage: Option[DockerImage],
      state: LifecycleState,
      containerState: ContainerState,
      specs: InstanceSpecs)(terminator: Instance => Future[Unit]): Instance =
    new Instance(
      id,
      ipAddress,
      placementGroup,
      Var(dockerImage),
      Var(state),
      Var(containerState),
      specs)(terminator)
}
