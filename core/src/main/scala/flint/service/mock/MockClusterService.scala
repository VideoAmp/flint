package flint
package service
package mock

import java.net.InetAddress
import java.util.UUID

import scala.concurrent.Future

import rx._

class MockClusterService(implicit ctx: Ctx.Owner) extends ClusterService {
  private lazy val instanceLifecycleManager = new InstanceLifecycleManager

  override val clusters = Var(Map.empty[ClusterId, ManagedCluster])

  override def launchCluster(spec: ClusterSpec): Future[ManagedCluster] = {
    import spec._
    val master = instance(masterInstanceType, spec.placementGroup)
    val workers = Var(
      (0 until numWorkers).map(_ => instance(workerInstanceType, spec.placementGroup)).toSeq)
    val cluster =
      MockManagedCluster(Cluster(id, Var(dockerImage), owner, ttl, idleTimeout, master, workers))(
        workers,
        workerInstanceType,
        spec.placementGroup)
    clusters() = clusters.now.updated(id, cluster)
    Future.successful(cluster)
  }

  private def instance(instanceType: String, placementGroup: Option[String]): Instance = {
    val id             = UUID.randomUUID.toString
    val lifecycleState = instanceLifecycleManager.createInstance(id.toString)
    val specs          = instanceSpecs(instanceType)

    Instance(id, InetAddress.getLoopbackAddress, placementGroup, lifecycleState, specs)(() =>
      terminateInstances(id))
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
    override def terminate(): Future[Unit] =
      terminateClusterInstances(cluster.master, cluster.workers)

    override protected def addWorkers0(count: Int) =
      Future.successful(workers() = cluster.workers.now ++ (0 until count).map(_ =>
          instance(workerInstanceType, placementGroup)))

    override protected def changeDockerImage0(dockerImage: DockerImage) =
      Future.successful(cluster.dockerImage.asVar() = dockerImage)
  }
}
