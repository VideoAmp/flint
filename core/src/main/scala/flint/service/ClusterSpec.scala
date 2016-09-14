package flint
package service

import java.time.Duration
import java.util.UUID

case class ClusterSpec(
    id: UUID,
    dockerImage: DockerImage,
    owner: String,
    ttl: Option[Duration],
    idleTimeout: Option[Duration],
    masterInstanceType: String,
    workerInstanceType: String,
    numWorkers: Int)
