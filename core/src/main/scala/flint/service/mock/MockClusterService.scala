package flint
package service
package mock

import java.time.Instant

import scala.concurrent.Future

import rx._

class MockClusterService(implicit ctx: Ctx.Owner) extends ClusterService {
  override val managementService = MockManagementService

  override val clusterSystem = new MockClusterSystem()

  override val instanceSpecs = mock.instanceSpecs

  override def getPlacementGroups(): Future[Seq[String]] =
    Future.successful("group 1" :: "group 2" :: Nil)

  override val subnets = Subnet("subnet_1", "az_1") :: Subnet("subnet_2", "az_2") :: Subnet(
    "subnet_3",
    "az_3") :: Nil

  override def getSpotPrices(subnet: Subnet, instanceTypes: String*): Future[Seq[SpotPrice]] = ???

  override def launchCluster(spec: ClusterSpec): Future[ManagedCluster] = {
    import spec._
    val master =
      clusterSystem.runInstance(Some(spec.dockerImage), masterInstanceType, spec.placementGroup)
    val workers = Var(
      (0 until numWorkers)
        .map(_ =>
          clusterSystem
            .runInstance(Some(spec.dockerImage), workerInstanceType, spec.placementGroup))
        .toSeq)
    val cluster =
      MockManagedCluster(
        Cluster(id, name, Var(dockerImage), ttl, idleTimeout, master, workers, Instant.now))(
        clusterSystem,
        workers,
        workerInstanceType,
        spec.placementGroup,
        ExtraTags(),
        None
      )

    clusterSystem.newClusters.asVar() = Seq(cluster)
    clusterSystem.clusters() = clusterSystem.clusters.now.updated(id, cluster)
    Future.successful(cluster)
  }

  override def launchSpotCluster(
      spec: ClusterSpec,
      workerBidPrice: BigDecimal): Future[ManagedCluster] =
    Future.failed(
      new RuntimeException(
        "Spot clusters are unsupported by the mock cluster service. Use launchCluster() instead"))
}
