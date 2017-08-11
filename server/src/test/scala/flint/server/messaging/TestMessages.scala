package flint
package server
package messaging

import flint.InstanceState._
import service.{ ClientRequested, ClusterSpec, ExtraTags }

import scala.concurrent.duration._
import scala.util.Random

object TestMessages {
  private val serverId    = "%8h".format(Random.nextInt)
  private val clusterId   = ClusterId()
  private val clusterName = "Sam's Cluster"
  private val dockerImage = DockerImage("test", "me")
  private val ttl         = Some(10 hours)
  private val idleTimeout = Some(15 minutes)
  private val clusterSpec =
    ClusterSpec(
      clusterId,
      clusterName,
      dockerImage,
      ttl,
      idleTimeout,
      "r3.xlarge",
      "c3.8xlarge",
      4,
      "subnet_1",
      Some("Placement Group"),
      ExtraTags())
  private val error      = Some("Something went wrong")
  private val instanceId = "instance123"

  val testMessages =
    Seq(
      AddWorkers(clusterId, 2),
      ChangeDockerImage(clusterId, dockerImage),
      ClusterLaunchAttempt(serverId, 0, clusterSpec, error),
      ClusterTerminationAttempt(serverId, 0, clusterId, ClientRequested, error),
      DockerImageChangeAttempt(serverId, 0, clusterId, dockerImage, error),
      InstanceContainerState(serverId, 0, instanceId, ContainerRunning),
      InstanceDockerImage(serverId, 0, instanceId, Some(dockerImage)),
      InstanceState(serverId, 0, instanceId, Running),
      LaunchCluster(clusterSpec),
      LaunchSpotCluster(clusterSpec, BigDecimal(1.23)),
      TerminateCluster(clusterId),
      TerminateWorker(instanceId),
      WorkerAdditionAttempt(serverId, 0, clusterId, 3, error),
      WorkerTerminationAttempt(serverId, 0, instanceId, error)
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
