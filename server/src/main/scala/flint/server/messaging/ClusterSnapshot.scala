package flint
package server
package messaging

import flint.service.ManagedCluster

import java.time.Instant

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
    subnet: Subnet,
    placementGroup: Option[String],
    workerBidPrice: Option[BigDecimal],
    launchedAt: Instant)

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
      managedCluster.subnet,
      managedCluster.placementGroup,
      managedCluster.workerBidPrice,
      launchedAt
    )
  }
}
