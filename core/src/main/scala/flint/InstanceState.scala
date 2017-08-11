package flint

sealed trait InstanceState {
  protected val name = toString
}

object InstanceState {
  def apply(name: String): InstanceState = name match {
    case Pending.name     => Pending
    case Running.name     => Running
    case Starting.name    => Starting
    case Terminated.name  => Terminated
    case Terminating.name => Terminating
  }

  case object Pending extends InstanceState

  case object Running extends InstanceState

  case object Starting extends InstanceState

  case object Terminated extends InstanceState

  case object Terminating extends InstanceState
}
