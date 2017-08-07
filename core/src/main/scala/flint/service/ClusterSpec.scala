package flint
package service

import scala.concurrent.duration.FiniteDuration

case class ClusterSpec(
    id: ClusterId,
    name: String,
    dockerImage: DockerImage,
    ttl: Option[FiniteDuration],
    idleTimeout: Option[FiniteDuration],
    masterInstanceType: String,
    workerInstanceType: String,
    numWorkers: Int,
    subnetId: String,
    placementGroup: Option[String] = None,
    extraInstanceTags: ExtraTags = ExtraTags())
