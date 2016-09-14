package flint
package service
package mock

import java.net.InetAddress
import java.util.UUID

import scala.concurrent.Future

import rx._

class MockClusterManager(implicit ctx: Ctx.Owner) extends ClusterManager {
  private lazy val instanceLifecycleManager = new InstanceLifecycleManager

  def launchCluster(spec: ClusterSpec): Future[Cluster] = {
    import spec._
    val master  = SparkMaster(instance(masterInstanceType), 7077)
    val workers = Var((0 until numWorkers).map(_ => instance(workerInstanceType)).toSeq)
    val cluster = Cluster(id, Var(dockerImage), owner, ttl, idleTimeout, master, workers)(() =>
      terminateClusterInstances(master, workers))
    Future.successful(cluster)
  }

  private def instance(instanceType: String): Instance = {
    val id             = UUID.randomUUID.toString
    val lifecycleState = instanceLifecycleManager.createInstance(id.toString)
    val specs          = instanceSpecs(instanceType)

    Instance(id, InetAddress.getLoopbackAddress, lifecycleState, specs)(() =>
      terminateInstances(id))
  }

  private def instanceSpecs(instanceType: String): InstanceSpecs = {
    val cores       = 4
    val memory      = 8
    val hourlyPrice = BigDecimal("1.25")

    InstanceSpecs(instanceType, cores, memory, hourlyPrice)
  }

  private def terminateClusterInstances(
      master: SparkMaster,
      workers: Var[Seq[Instance]]): Future[Unit] =
    terminateInstances((master.instance +: workers.now).map(_.id): _*)

  private def terminateInstances(instanceIds: String*): Future[Unit] = {
    instanceLifecycleManager.terminateInstances(instanceIds.map(_.toString): _*)
    Future.successful(())
  }
}
