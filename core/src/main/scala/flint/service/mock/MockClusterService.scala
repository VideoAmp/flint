package flint
package service
package mock

import java.net.InetAddress
import java.util.UUID

import scala.concurrent.Future

import rx._

class MockClusterService(implicit ctx: Ctx.Owner) extends ClusterService {
  val managementService = MockManagementService

  private lazy val instanceLifecycleManager = new InstanceLifecycleManager

  override val clusterSystem = new ClusterSystem {
    override val clusters = Var(Map.empty[ClusterId, ManagedCluster])

    override val newCluster = Var(Option.empty[ManagedCluster])
  }

  override def launchCluster(spec: ClusterSpec): Future[ManagedCluster] = {
    import spec._
    val master = instance(Some(spec.dockerImage), masterInstanceType, spec.placementGroup)
    val workers = Var(
      (0 until numWorkers)
        .map(_ => instance(Some(spec.dockerImage), workerInstanceType, spec.placementGroup))
        .toSeq)
    val cluster =
      MockManagedCluster(Cluster(id, Var(dockerImage), owner, ttl, idleTimeout, master, workers))(
        workers,
        workerInstanceType,
        spec.placementGroup)
    clusterSystem.newCluster() = Some(cluster)
    clusterSystem.clusters() = clusterSystem.clusters.now.updated(id, cluster)
    Future.successful(cluster)
  }

  private def instance(
      dockerImage: Option[DockerImage],
      instanceType: String,
      placementGroup: Option[String]): Instance = {
    val id             = UUID.randomUUID.toString
    val lifecycleState = instanceLifecycleManager.createInstance(id.toString)
    val specs          = instanceSpecs(instanceType)

    Instance(
      id,
      InetAddress.getLoopbackAddress,
      placementGroup,
      Var(dockerImage),
      lifecycleState,
      Var(ContainerRunning),
      specs)(() => terminateInstances(id))
  }

  private def instanceSpecs(instanceType: String): InstanceSpecs = {
    val cores       = 4
    val memory      = 8
    val hourlyPrice = BigDecimal("1.25")

    InstanceSpecs(instanceType, cores, memory, Storage(1, 32), hourlyPrice)
  }

  private def terminateClusterInstances(
      master: Instance,
      workers: Rx[Seq[Instance]]): Future[Unit] =
    terminateInstances((master +: workers.now).map(_.id): _*)

  private def terminateInstances(instanceIds: String*): Future[Unit] = {
    instanceLifecycleManager.terminateInstances(instanceIds.map(_.toString): _*)
    Future.successful(())
  }

  private case class MockManagedCluster(cluster: Cluster)(
      workers: Var[Seq[Instance]],
      workerInstanceType: String,
      placementGroup: Option[String])
      extends ManagedCluster {
    override protected val managementService = MockManagementService

    override def terminate(): Future[Unit] =
      terminateClusterInstances(cluster.master, cluster.workers)

    override protected def addWorkers0(count: Int) =
      Future.successful {
        val newWorkers = cluster.workers.now ++ (0 until count).map(_ =>
            instance(Some(cluster.dockerImage.now), workerInstanceType, placementGroup))
        newWorkers.map(Some(_)).foreach(newWorker.asVar() = _)
        workers() = newWorkers
      }

    override protected def changeDockerImage0(dockerImage: DockerImage) =
      Future.successful(cluster.dockerImage.asVar() = dockerImage)
  }
}
