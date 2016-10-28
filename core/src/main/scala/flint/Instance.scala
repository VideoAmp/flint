package flint

import java.net.InetAddress

import scala.concurrent.Future
import scala.util.Success

import rx._

case class Instance(
    id: String,
    ipAddress: InetAddress,
    placementGroup: Option[String],
    dockerImage: Rx[Option[DockerImage]],
    instanceState: Rx[LifecycleState],
    containerState: Rx[ContainerState],
    specs: InstanceSpecs)(terminator: () => Future[Unit])
    extends Killable {

  override def terminate(): Future[Unit] =
    terminator() andThen {
      case Success(_) => containerState.asVar() = ContainerStopped
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
      ipAddress: InetAddress,
      placementGroup: Option[String],
      dockerImage: Option[DockerImage],
      instanceState: LifecycleState,
      containerState: ContainerState,
      specs: InstanceSpecs)(terminator: () => Future[Unit]): Instance =
    new Instance(
      id,
      ipAddress,
      placementGroup,
      Var(dockerImage),
      Var(instanceState),
      Var(containerState),
      specs)(terminator)
}
