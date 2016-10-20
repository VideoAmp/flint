package flint
package server
package messaging

import service.ClusterSpec

import java.time.Duration

object TestMessages {
  private val clusterId   = ClusterId()
  private val dockerImage = DockerImage("test", "me")
  private val owner       = "Sam"
  private val ttl         = Some(Duration.ofHours(10))
  private val idleTimeout = Some(Duration.ofMinutes(15))
  private val clusterSpec =
    ClusterSpec(
      clusterId,
      dockerImage,
      owner,
      ttl,
      idleTimeout,
      "r3.xlarge",
      "c3.8xlarge",
      4,
      Some("Placement Group"))
  private val error      = Some("Something went wrong")
  private val instanceId = "instance123"

  val testMessages =
    Seq(
      AddWorkers(clusterId, 2),
      ChangeDockerImage(clusterId, dockerImage),
      ClusterLaunchAttempt(0, clusterSpec, error),
      ClusterTerminationAttempt(0, clusterId, ClientRequested, error),
      DockerImageChangeAttempt(0, clusterId, dockerImage, error),
      LaunchCluster(clusterSpec),
      TerminateCluster(clusterId),
      TerminateWorker(instanceId),
      WorkerAdditionAttempt(0, clusterId, 3, error),
      WorkerTerminationAttempt(0, instanceId, IdleTimeout, error)
    )
}
