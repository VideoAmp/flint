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
    placementGroup: Option[String] = None)

object ClusterSpec {
  // Convenience constructors
  def apply(
      dockerImage: DockerImage,
      owner: String,
      ttl: Option[FiniteDuration],
      idleTimeout: Option[FiniteDuration],
      masterInstanceType: String,
      workerInstanceType: String,
      numWorkers: Int): ClusterSpec =
    ClusterSpec(
      ClusterId(),
      dockerImage,
      owner,
      ttl,
      idleTimeout,
      masterInstanceType,
      workerInstanceType,
      numWorkers,
      None)

  def apply(
      dockerImage: DockerImage,
      owner: String,
      ttl: Option[FiniteDuration],
      idleTimeout: Option[FiniteDuration],
      masterInstanceType: String,
      workerInstanceType: String,
      numWorkers: Int,
      placementGroup: Option[String]): ClusterSpec =
    ClusterSpec(
      ClusterId(),
      dockerImage,
      owner,
      ttl,
      idleTimeout,
      masterInstanceType,
      workerInstanceType,
      numWorkers,
      placementGroup)
}
