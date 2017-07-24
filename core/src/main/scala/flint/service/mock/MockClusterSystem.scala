package flint
package service
package mock

import java.net.InetAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import rx._

private[mock] class MockClusterSystem()(implicit protected val ctx: Ctx.Owner)
    extends ClusterSystem
    with LazyLogging {
  override val clusters = Var(Map.empty[ClusterId, MockManagedCluster])

  private val instanceStateSimulatorMap = new TrieMap[String, InstanceStateSimulator]

  def runInstance(
      dockerImage: Option[DockerImage],
      instanceType: String,
      placementGroup: Option[String]): Instance = {
    val id             = UUID.randomUUID.toString
    val lifecycleState = Var[LifecycleState](Pending)
    lifecycleState.collectFirst {
      case Terminated =>
        instanceStateSimulatorMap.remove(id).foreach(_.cancel)
    }
    val containerState = Var[ContainerState](ContainerPending)
    val stateSimulator = new InstanceStateSimulator(lifecycleState, containerState)
    instanceStateSimulatorMap(id) = stateSimulator
    val specs        = instanceSpecsMap(instanceType)
    val launchedAt   = Instant.now
    val terminatedAt = Var(None)

    Instance(
      id,
      Var(Some(InetAddress.getLoopbackAddress)),
      Var(Some(Subnet("subnet_1", "az_1"))),
      placementGroup,
      Var(dockerImage),
      lifecycleState,
      containerState,
      specs,
      launchedAt,
      terminatedAt
    )(instance => Future.successful(terminateInstances(instance.id)))
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
      instanceStateSimulatorMap.get(instanceId).foreach(_.terminateInstance)
    }
}
