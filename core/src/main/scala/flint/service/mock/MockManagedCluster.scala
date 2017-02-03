package flint
package service
package mock

import scala.concurrent.Future

import rx._

private[mock] case class MockManagedCluster(cluster: Cluster)(
    clusterService: MockClusterService,
    workers: Var[Seq[Instance]],
    override val workerInstanceType: String,
    placementGroup: Option[String])
    extends ManagedCluster {
  override protected val managementService = MockManagementService

  override protected def terminate0(): Future[Unit] =
    clusterService.terminateClusterInstances(cluster.master, cluster.workers)

  override protected def addWorkers0(count: Int) =
    Future.successful {
      val newWorkers = cluster.workers.now ++ (0 until count).map(
          _ =>
            clusterService
              .instance(Some(cluster.dockerImage.now), workerInstanceType, placementGroup))
      this.newWorkers.asVar() = newWorkers
      workers() = newWorkers
      newWorkers
    }

  override protected def changeDockerImage0(dockerImage: DockerImage) =
    Future.successful(cluster.dockerImage.asVar() = dockerImage)
}
