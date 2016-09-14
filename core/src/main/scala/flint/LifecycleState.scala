package flint

sealed trait LifecycleState

object LifecycleState {
  // TODO: Move to Cluster?
  def reduce(state1: LifecycleState, state2: LifecycleState): LifecycleState =
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

case object Running extends LifecycleState

case object Starting extends LifecycleState

case object Terminated extends LifecycleState

case object Terminating extends LifecycleState

case object Pending extends LifecycleState
