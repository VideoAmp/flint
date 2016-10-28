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
      ClusterLaunchAttempt(clusterSpec, error),
      ClusterTerminationAttempt(clusterId, ClientRequested, error),
      DockerImageChangeAttempt(clusterId, dockerImage, error),
      InstanceContainerState(instanceId, ContainerRunning),
      InstanceDockerImage(instanceId, Some(dockerImage)),
      InstanceState(instanceId, Running),
      LaunchCluster(clusterSpec),
      TerminateCluster(clusterId),
      TerminateWorker(instanceId),
      WorkerAdditionAttempt(clusterId, 3, error),
      WorkerTerminationAttempt(instanceId, IdleTimeout, error)
    )

  def main(args: Array[String]): Unit = {
    // scalastyle:off println
    def printMessages(messages: Seq[Message]): Unit =
      messages
        .sortBy(_.getClass.getSimpleName)
        .map(MessageCodec.encode(_, pretty = true))
        .foreach(println)

    println("Server message examples:")
    printMessages(testMessages.collect { case m: ServerMessage => m })
    println
    println("Client message examples:")
    printMessages(testMessages.collect { case m: ClientMessage => m })
    // scalastyle:on println
  }
}
