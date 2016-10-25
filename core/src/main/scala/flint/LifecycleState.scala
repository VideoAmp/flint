package flint

sealed trait LifecycleState {
  protected val name = toString
}

object LifecycleState {
  def apply(name: String): LifecycleState = name match {
    case Pending.name     => Pending
    case Running.name     => Running
    case Starting.name    => Starting
    case Terminated.name  => Terminated
    case Terminating.name => Terminating
  }
}

case object Pending extends LifecycleState

case object Running extends LifecycleState

case object Starting extends LifecycleState

case object Terminated extends LifecycleState

case object Terminating extends LifecycleState
