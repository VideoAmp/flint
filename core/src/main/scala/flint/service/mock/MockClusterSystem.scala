package flint
package service
package mock

import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.{ ScheduledFuture, TimeUnit }

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import rx._

private[mock] class MockClusterSystem(clusterService: MockClusterService)(
    implicit protected val ctx: Ctx.Owner)
    extends ClusterSystem
    with LazyLogging {
  override val clusters = Var(Map.empty[ClusterId, MockManagedCluster])

  private val instanceLifecycleStateMap =
    new TrieMap[String, (InstanceLifecycleSimulator, ScheduledFuture[_])]

  def runInstance(
      dockerImage: Option[DockerImage],
      instanceType: String,
      placementGroup: Option[String]): Instance = {
    val id             = UUID.randomUUID.toString
    val lifecycleState = Var[LifecycleState](Pending)
    lifecycleState.collectFirst {
      case Terminated =>
        instanceLifecycleStateMap.remove(id).foreach(_._2.cancel(false))
    }
    val lifecycleSimulator = new InstanceLifecycleSimulator(lifecycleState)
    val future =
      simulationExecutorService.scheduleAtFixedRate(lifecycleSimulator, 3, 3, TimeUnit.SECONDS)
    instanceLifecycleStateMap(id) = (lifecycleSimulator, future)
    val specs = instanceSpecsMap(instanceType)

    Instance(
      id,
      Var(Some(InetAddress.getLoopbackAddress)),
      placementGroup,
      Var(dockerImage),
      lifecycleState,
      Var(ContainerRunning),
      specs)(instance => Future.successful(terminateInstances(instance.id)))
  }

  def terminateCluster(managedCluster: MockManagedCluster): Unit = {
    val cluster = managedCluster.cluster
    val master  = cluster.master
    val workers = cluster.workers.now
    terminateInstances((master +: workers).map(_.id): _*)
    master.state.collectFirst {
      case Terminated =>
        val removeCluster = new Runnable {
          override def run() = {
            logger.debug(s"Removing cluster ${cluster.id}")
            clusters.asVar() = clusters.now - cluster.id
            removedClusters.asVar() = cluster.id :: Nil
          }
        }
        simulationExecutorService.schedule(removeCluster, 5, TimeUnit.SECONDS)
    }
  }

  def terminateInstances(instanceIds: String*): Unit =
    instanceIds.foreach { instanceId =>
      instanceLifecycleStateMap.get(instanceId).foreach(_._1.lifecycleState() = Terminating)
    }
}
