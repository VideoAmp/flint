package flint

import java.net.InetAddress

import scala.concurrent.Future

import rx._

case class Instance(
    id: String,
    ipAddress: InetAddress,
    placementGroup: Option[String],
    lifecycleState: Rx[LifecycleState],
    specs: InstanceSpecs)(terminator: () => Future[Unit])
    extends Lifecycle
    with Killable {

  override def terminate(): Future[Unit] = terminator()

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
      lifecycleState: LifecycleState,
      specs: InstanceSpecs)(terminator: () => Future[Unit]): Instance =
    new Instance(id, ipAddress, placementGroup, Var(lifecycleState), specs)(terminator)
}
