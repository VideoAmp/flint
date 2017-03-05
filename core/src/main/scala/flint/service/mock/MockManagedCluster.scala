package flint
package service
package mock

import java.util.concurrent.TimeUnit

import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import rx._

private[mock] case class MockManagedCluster(cluster: Cluster)(
    clusterSystem: MockClusterSystem,
    workers: Var[Seq[Instance]],
    override val workerInstanceType: String,
    override val workerBidPrice: Option[BigDecimal],
    placementGroup: Option[String])(implicit protected val ctx: Ctx.Owner)
    extends ManagedCluster
    with LazyLogging {
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

  private def registerWorkerTerminationHandler(worker: Instance) =
    worker.state collectFirst {
      case Terminated =>
        val removeWorker = new Runnable {
          override def run() = {
            logger.debug(s"Removing worker ${worker.id}")
            workers() = workers.now.filterNot(_ == worker)
            removedWorkers.asVar() = worker.id :: Nil
          }
        }
        simulationExecutorService.schedule(removeWorker, 3, TimeUnit.SECONDS)
    }
}
