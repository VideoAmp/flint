package flint
package service

import rx._

trait ClusterSystem {
  protected implicit val ctx: Ctx.Owner

  val clusters: Rx[Map[ClusterId, ManagedCluster]]

  final lazy val runningClusters: Rx[Map[ClusterId, ManagedCluster]] = Rx {
    clusters().filter(_._2.cluster.master.containerState() == ContainerRunning)
  }

  val newClusters: Rx[Seq[ManagedCluster]]
}
