package flint
package service

import ContainerState._

import rx._

trait ClusterSystem {
  protected implicit val ctx: Ctx.Owner

  val clusters: Rx[Map[ClusterId, ManagedCluster]]

  final lazy val runningClusters: Rx[Map[ClusterId, ManagedCluster]] = Rx {
    clusters().filter(_._2.cluster.master.effectiveContainerState() == ContainerRunning)
  }

  final val newClusters: Rx[Seq[ManagedCluster]] = Var(Seq.empty[ManagedCluster])
  final val removedClusters: Rx[Seq[ClusterId]]  = Var(Seq.empty[ClusterId])
}
