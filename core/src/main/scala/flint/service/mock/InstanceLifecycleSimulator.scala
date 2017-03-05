package flint
package service
package mock

import rx._

private[mock] case class InstanceLifecycleSimulator(lifecycleState: Var[LifecycleState])
    extends Runnable {
  override def run() =
    lifecycleState() = lifecycleState.now match {
      case Pending     => Starting
      case Starting    => Running
      case Terminating => Terminated
      case state       => state
    }
}
