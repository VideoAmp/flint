package flint
package service

import scala.concurrent.duration.FiniteDuration

case class ClusterSpec(
    id: ClusterId,
    dockerImage: DockerImage,
    owner: String,
    ttl: Option[FiniteDuration],
    idleTimeout: Option[FiniteDuration],
    masterInstanceType: String,
    workerInstanceType: String,
    numWorkers: Int,
    subnetId: String,
    placementGroup: Option[String] = None,
    extraInstanceTags: ExtraTags = ExtraTags())
