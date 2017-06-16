package flint
package service

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import rx._

trait ManagedCluster extends Killable {
  val cluster: Cluster

  final val newWorkers: Rx[Seq[Instance]]   = Var(Seq.empty[Instance])
  final val removedWorkers: Rx[Seq[String]] = Var(Seq.empty[String])

  val workerInstanceType: String
  val workerBidPrice: Option[BigDecimal]

  protected val managementService: ManagementService

  final def addWorkers(count: Int): Future[Seq[Instance]] =
    Future
      .fromTry(Try {
        require(
          cluster.master.state.now == Running,
          "cluster master must be running to add workers")
        require(count > 0, "worker count must be positive")
      })
      .flatMap(_ => addWorkers0(count))

  protected def addWorkers0(count: Int): Future[Seq[Instance]]

  final def changeDockerImage(dockerImage: DockerImage): Future[Unit] =
    Future
      .fromTry(
        Try(
          require(
            cluster.master.state.now == Running,
            "cluster master must be running to change docker image")))
      .flatMap(_ => changeDockerImage0(dockerImage))

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
            FiniteDuration(5, "min")
          )
          .map(_ => ())
      }
  }

  final def terminate(): Future[Unit] =
    Future
      .fromTry(Try {
        require(cluster.master.state.now != Terminating, "cluster is already terminating")
        require(cluster.master.state.now != Terminated, "cluster is already terminated")
      })
      .flatMap(_ => terminate0)

  protected def terminate0(): Future[Unit]

  override def toString(): String = "Managed" + cluster.toString
}
