package flint
package service
package mock

import java.util.concurrent.TimeUnit

import rx._

private[mock] case class InstanceStateSimulator(
    lifecycleState: Var[LifecycleState],
    containerState: Var[ContainerState]) {
  private val runnable = new Runnable {
    override def run() = {
      val (nextLifecycleState, nextContainerState) =
        (lifecycleState.now, containerState.now) match {
          case (_, ContainerStopping)       => (Running, ContainerStopped)
          case (Running, ContainerStopped)  => (Terminating, ContainerStopped)
          case (Pending, _)                 => (Starting, ContainerPending)
          case (Starting, _)                => (Running, ContainerPending)
          case (Running, ContainerPending)  => (Running, ContainerStarting)
          case (Running, ContainerStarting) => (Running, ContainerRunning)
          case (Terminating, _)             => (Terminated, ContainerStopped)
          case state                        => state
        }

      lifecycleState() = nextLifecycleState
      containerState() = nextContainerState
    }
  }

  private val future =
    simulationExecutorService.scheduleAtFixedRate(runnable, 3, 3, TimeUnit.SECONDS)

  def cancel(): Unit = future.cancel(false)

  def terminateInstance(): Unit =
    containerState() = ContainerStopping
}
