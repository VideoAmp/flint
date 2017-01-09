package flint
package server
package messaging

import scala.concurrent.duration.FiniteDuration

private[messaging] case class ClusterSnapshot(
    id: ClusterId,
    dockerImage: DockerImage,
    owner: String,
    ttl: Option[FiniteDuration],
    idleTimeout: Option[FiniteDuration],
    master: InstanceSnapshot,
    workers: List[InstanceSnapshot])

private[messaging] object ClusterSnapshot {
  def apply(cluster: Cluster): ClusterSnapshot = {
    import cluster._

    ClusterSnapshot(
      id,
      dockerImage.now,
      owner,
      ttl,
      idleTimeout,
      InstanceSnapshot(master),
      workers.now.toList.map(InstanceSnapshot(_)))
  }
}
