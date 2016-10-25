package flint

sealed trait ContainerState {
  protected val name = toString
}

object ContainerState {
  def apply(name: String): ContainerState = name match {
    case ContainerPending.name  => ContainerPending
    case ContainerRunning.name  => ContainerRunning
    case ContainerStarting.name => ContainerStarting
    case ContainerStopped.name  => ContainerStopped
    case ContainerStopping.name => ContainerStopping
  }
}

case object ContainerPending extends ContainerState

case object ContainerRunning extends ContainerState

case object ContainerStarting extends ContainerState

case object ContainerStopped extends ContainerState

case object ContainerStopping extends ContainerState
