package flint

sealed trait ContainerState {
  protected val name = toString

  def constrainedBy(lifecycleState: LifecycleState): ContainerState
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

case object ContainerPending extends ContainerState {
  override def constrainedBy(lifecycleState: LifecycleState): ContainerState =
    lifecycleState match {
      case Terminating => ContainerStopping
      case Terminated  => ContainerStopped
      case _           => this
    }
}

case object ContainerRunning extends ContainerState {
  override def constrainedBy(lifecycleState: LifecycleState): ContainerState =
    lifecycleState match {
      case Pending     => ContainerPending
      case Starting    => ContainerStarting
      case Terminating => ContainerStopping
      case Terminated  => ContainerStopped
      case _           => this
    }
}

case object ContainerStarting extends ContainerState {
  override def constrainedBy(lifecycleState: LifecycleState): ContainerState =
    lifecycleState match {
      case Pending     => ContainerPending
      case Terminating => ContainerStopping
      case Terminated  => ContainerStopped
      case _           => this
    }
}

case object ContainerStopped extends ContainerState {
  override def constrainedBy(lifecycleState: LifecycleState): ContainerState =
    lifecycleState match {
      case _ => this
    }
}

case object ContainerStopping extends ContainerState {
  override def constrainedBy(lifecycleState: LifecycleState): ContainerState =
    lifecycleState match {
      case Pending    => ContainerStopped
      case Terminated => ContainerStopped
      case _          => this
    }
}
