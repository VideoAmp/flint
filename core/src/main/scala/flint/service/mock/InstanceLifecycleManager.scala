package flint
package service
package mock

import java.util.concurrent.{ Executors, ScheduledFuture, TimeUnit }

import scala.collection.concurrent.TrieMap

import rx._

private[mock] class InstanceLifecycleManager {
  private val instanceLifecycleStateMap =
    new TrieMap[String, (LifecycleSimulator, ScheduledFuture[_])]

  private val scheduler = Executors.newSingleThreadScheduledExecutor(flintThreadFactory)

  private class LifecycleSimulator(instanceId: String, val lifecycleState: Var[LifecycleState])
      extends Runnable {
    override def run() = {
      if (lifecycleState.now == Terminated) {
        instanceLifecycleStateMap.remove(instanceId).foreach(_._2.cancel(false))
      } else {
        lifecycleState() = lifecycleState.now match {
          case Pending     => Starting
          case Starting    => Running
          case Terminating => Terminated
          case state       => state
        }
      }
    }
  }

  private[mock] def createInstance(instanceId: String): Rx[LifecycleState] = {
    val lifecycleState     = Var[LifecycleState](Pending)
    val lifecycleSimulator = new LifecycleSimulator(instanceId, lifecycleState)
    val future =
      scheduler.scheduleAtFixedRate(lifecycleSimulator, 3, 3, TimeUnit.SECONDS)
    instanceLifecycleStateMap(instanceId) = (lifecycleSimulator, future)
    lifecycleState
  }

  private[mock] def terminateInstances(instanceIds: String*): Unit =
    instanceIds.foreach { instanceId =>
      instanceLifecycleStateMap.get(instanceId).foreach(_._1.lifecycleState() = Terminating)
    }
}
