package flint
package service

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import rx._

trait ManagedCluster extends Killable {
  val cluster: Cluster

  final val newWorkers: Rx[Seq[Instance]] = Var(Seq.empty[Instance])

  protected val managementService: ManagementService

  final def addWorkers(count: Int): Future[Seq[Instance]] = {
    require(cluster.state.now == Running, "Cluster must be running to add workers")
    require(count > 0, "Worker count must be positive")
    addWorkers0(count)
  }

  protected def addWorkers0(count: Int): Future[Seq[Instance]]

  final def changeDockerImage(dockerImage: DockerImage): Future[Unit] = {
    require(cluster.state.now == Running, "Cluster must be running to change its Docker image")
    changeDockerImage0(dockerImage)
  }

  protected def changeDockerImage0(dockerImage: DockerImage): Future[Unit] = {
    val instances = cluster.master +: cluster.workers.now
    managementService
      .sendCommand(
        instances,
        s"Flint: stop docker container ${cluster.dockerImage.now.canonicalName}",
        "/sbin/stop-spark-container.sh",
        FiniteDuration(30, "s"))
      .flatMap { stoppedInstances =>
        managementService
          .sendCommand(
            stoppedInstances,
            s"Flint: start docker container ${dockerImage.canonicalName}",
            s"""/sbin/start-spark-container.sh "${dockerImage.canonicalName}"""",
            FiniteDuration(5, "min"))
          .map(_ => ())
      }
  }

  override def toString(): String = "Managed" + cluster.toString
}
