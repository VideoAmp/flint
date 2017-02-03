package flint
package service
package mock

import java.net.InetAddress
import java.time.Instant
import java.util.UUID

import scala.concurrent.Future

import rx._

class MockClusterService(implicit ctx: Ctx.Owner) extends ClusterService {
  override val managementService = MockManagementService

  private lazy val instanceLifecycleManager = new InstanceLifecycleManager

  override val clusterSystem = new ClusterSystem {
    override protected implicit val ctx = MockClusterService.this.ctx

    override val clusters = Var(Map.empty[ClusterId, ManagedCluster])

    override val newClusters = Var(Seq.empty[ManagedCluster])
  }

  override val instanceSpecs = Seq(
    InstanceSpecs("t2.micro", 1, GiB(1), "0.013"),
    InstanceSpecs("c3.8xlarge", 32, GiB(52), InstanceStorageSpec(2, GiB(320)), "1.68"),
    InstanceSpecs("r3.large", 2, GiB(13), InstanceStorageSpec(1, GiB(32)), "0.166"),
    InstanceSpecs("r3.8xlarge", 32, GiB(236), InstanceStorageSpec(2, GiB(320)), "2.66"))

  private val instanceSpecsMap: Map[String, InstanceSpecs] =
    instanceSpecs.map(specs => specs.instanceType -> specs).toMap

  override def getSpotPrices(instanceTypes: String*): Future[Seq[SpotPrice]] = ???

  override def launchCluster(spec: ClusterSpec): Future[ManagedCluster] = {
    import spec._
    val master = instance(Some(spec.dockerImage), masterInstanceType, spec.placementGroup)
    val workers = Var(
      (0 until numWorkers)
        .map(_ => instance(Some(spec.dockerImage), workerInstanceType, spec.placementGroup))
        .toSeq)
    val cluster =
      MockManagedCluster(
        Cluster(id, Var(dockerImage), owner, ttl, idleTimeout, master, workers, Instant.now))(
        this,
        workers,
        workerInstanceType,
        None,
        spec.placementGroup)
    clusterSystem.newClusters() = Seq(cluster)
    clusterSystem.clusters() = clusterSystem.clusters.now.updated(id, cluster)
    Future.successful(cluster)
  }

  override def launchSpotCluster(
      spec: ClusterSpec,
      workerBidPrice: BigDecimal): Future[ManagedCluster] =
    throw new RuntimeException(
      "Spot clusters are unsupported by the mock cluster service. Use launchCluster() instead")

  private[mock] def instance(
      dockerImage: Option[DockerImage],
      instanceType: String,
      placementGroup: Option[String]): Instance = {
    val id             = UUID.randomUUID.toString
    val lifecycleState = instanceLifecycleManager.createInstance(id.toString)
    val specs          = instanceSpecsMap(instanceType)

    Instance(
      id,
      Var(Some(InetAddress.getLoopbackAddress)),
      placementGroup,
      Var(dockerImage),
      lifecycleState,
      Var(ContainerRunning),
      specs)(instance => terminateInstances(instance.id))
  }

  private[mock] def terminateClusterInstances(
      master: Instance,
      workers: Rx[Seq[Instance]]): Future[Unit] =
    terminateInstances((master +: workers.now).map(_.id): _*)

  private def terminateInstances(instanceIds: String*): Future[Unit] = {
    instanceLifecycleManager.terminateInstances(instanceIds.map(_.toString): _*)
    Future.successful(())
  }
}
