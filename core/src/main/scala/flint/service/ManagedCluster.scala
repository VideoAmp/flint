package flint
package service

import scala.concurrent.Future

trait ManagedCluster extends Killable {
  val cluster: Cluster

  final def addWorkers(count: Int): Future[Unit] = {
    require(cluster.lifecycleState.now == Running, "Cluster must be running to add workers")
    require(count > 0, "Worker count must be positive")
    addWorkers0(count)
  }

  protected def addWorkers0(count: Int): Future[Unit]

  final def changeDockerImage(dockerImage: DockerImage): Future[Unit] = {
    require(
      cluster.lifecycleState.now == Running,
      "Cluster must be running to change its Docker image")
    changeDockerImage0(dockerImage)
  }

  protected def changeDockerImage0(dockerImage: DockerImage): Future[Unit]

  override def toString(): String = cluster.toString
}
