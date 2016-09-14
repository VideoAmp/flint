package flint

import java.net.InetAddress

import scala.concurrent.Future

import rx.Rx

case class Instance(
    id: String,
    ipAddress: InetAddress,
    lifecycleState: Rx[LifecycleState],
    specs: InstanceSpecs)(terminator: () => Future[Unit])
    extends Lifecycle {

  override def terminate(): Future[Unit] = terminator()

  override def equals(other: Any): Boolean = other match {
    case otherInstance: Instance => id == otherInstance.id
    case _                       => false
  }

  override def hashCode(): Int = id.hashCode
}
