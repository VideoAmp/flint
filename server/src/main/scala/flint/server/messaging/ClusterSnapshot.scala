package flint
package server
package messaging

import flint.service.ManagedCluster

import scala.concurrent.duration.FiniteDuration

private[messaging] case class ClusterSnapshot(
    id: ClusterId,
    name: String,
    dockerImage: DockerImage,
    ttl: Option[FiniteDuration],
    idleTimeout: Option[FiniteDuration],
    master: InstanceSnapshot,
    workers: List[InstanceSnapshot],
    workerInstanceType: String,
    workerBidPrice: Option[BigDecimal])

private[messaging] object ClusterSnapshot {
  def apply(managedCluster: ManagedCluster): ClusterSnapshot = {
    import managedCluster.cluster._

    ClusterSnapshot(
      id,
      name,
      dockerImage.now,
      ttl,
      idleTimeout,
      InstanceSnapshot(master),
      workers.now.toList.map(InstanceSnapshot(_)),
      managedCluster.workerInstanceType,
      managedCluster.workerBidPrice
    )
  }
}
