package flint
package service

import java.time.Duration

case class ClusterSpec(
    id: ClusterId,
    dockerImage: DockerImage,
    owner: String,
    ttl: Option[Duration],
    idleTimeout: Option[Duration],
    masterInstanceType: String,
    workerInstanceType: String,
    numWorkers: Int,
    placementGroup: Option[String] = None)
