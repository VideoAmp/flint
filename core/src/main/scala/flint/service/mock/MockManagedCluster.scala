package flint
package service
package mock

import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import rx._

private[mock] case class MockManagedCluster(cluster: Cluster)(
    clusterSystem: MockClusterSystem,
    workers: Var[Seq[Instance]],
    override val workerInstanceType: String,
    override val subnet: Subnet,
    override val placementGroup: Option[String],
    override val extraInstanceTags: ExtraTags,
    override val workerBidPrice: Option[BigDecimal])(implicit protected val ctx: Ctx.Owner)
    extends ManagedCluster
    with LazyLogging {
  registerInstanceTerminationHandler(cluster.master)
  workers.now.foreach(registerWorkerTerminationHandler)

  override protected val managementService = MockManagementService

  override protected def terminate0(): Future[Unit] =
    Future.successful(clusterSystem.terminateCluster(this))

  override protected def addWorkers0(count: Int) =
    Future.successful {
      val newWorkers = (0 until count).map(
        _ =>
          clusterSystem
            .runInstance(Some(cluster.dockerImage.now), workerInstanceType, placementGroup))
      this.newWorkers.asVar() = newWorkers
      workers() = cluster.workers.now ++ newWorkers
      newWorkers.foreach(registerWorkerTerminationHandler)
      newWorkers
    }

  override protected def changeDockerImage0(dockerImage: DockerImage) =
    Future.successful(cluster.dockerImage.asVar() = dockerImage)

  private def registerInstanceTerminationHandler(instance: Instance) =
    instance.state.collectFirst {
      case Terminated =>
        val stripIpAddress = new Runnable {
          override def run() = {
            logger.debug(s"Setting instance ${instance.id} ip address to None")
            instance.ipAddress.asVar() = None
            logger.debug(s"Setting instance ${instance.id} subnet to None")
            instance.subnet.asVar() = None
            val terminatedAt = Instant.now
            logger.debug(s"Setting instance ${instance.id} termination time to $terminatedAt")
            instance.terminatedAt.asVar() = Some(terminatedAt)
          }
        }
        simulationExecutorService.schedule(stripIpAddress, 3, TimeUnit.SECONDS)
    }

  private def registerWorkerTerminationHandler(worker: Instance) = {
    registerInstanceTerminationHandler(worker)
    worker.state.collectFirst {
      case Terminated =>
        val removeWorker = new Runnable {
          override def run() = {
            logger.debug(s"Removing worker ${worker.id}")
            workers() = workers.now.filterNot(_ == worker)
            removedWorkers.asVar() = worker.id :: Nil
          }
        }
        simulationExecutorService.schedule(removeWorker, 8, TimeUnit.SECONDS)
    }
  }
}
