package flint
package server
package messaging

import service.{ ClusterSpec, ClusterTerminationReason }

import java.net.InetAddress

private[messaging] sealed trait Message

private[messaging] sealed trait ClientMessage extends Message

private[messaging] sealed trait ServerMessage extends Message {
  val serverId: String
  val messageNo: Int
}

private[messaging] final case class AddWorkers(clusterId: ClusterId, count: Int)
    extends ClientMessage

private[messaging] final case class ChangeDockerImage(
    clusterId: ClusterId,
    dockerImage: DockerImage)
    extends ClientMessage

private[messaging] final case class ClusterLaunchAttempt(
    serverId: String,
    messageNo: Int,
    clusterSpec: ClusterSpec,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class ClusterTerminationAttempt(
    serverId: String,
    messageNo: Int,
    clusterId: ClusterId,
    reason: ClusterTerminationReason,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class ClustersAdded(
    serverId: String,
    messageNo: Int,
    clusters: List[ClusterSnapshot])
    extends ServerMessage

private[messaging] final case class ClustersRemoved(
    serverId: String,
    messageNo: Int,
    clusterIds: List[ClusterId])
    extends ServerMessage

private[messaging] final case class DockerImageChangeAttempt(
    serverId: String,
    messageNo: Int,
    clusterId: ClusterId,
    dockerImage: DockerImage,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class DockerImageChangeRequest(
    serverId: String,
    messageNo: Int,
    clusterId: ClusterId,
    dockerImage: DockerImage)
    extends ServerMessage

private[messaging] final case class InstanceContainerState(
    serverId: String,
    messageNo: Int,
    instanceId: String,
    containerState: ContainerState)
    extends ServerMessage

private[messaging] final case class InstanceDockerImage(
    serverId: String,
    messageNo: Int,
    instanceId: String,
    dockerImage: Option[DockerImage])
    extends ServerMessage

private[messaging] final case class InstanceIpAddress(
    serverId: String,
    messageNo: Int,
    instanceId: String,
    ipAddress: Option[InetAddress])
    extends ServerMessage

private[messaging] final case class InstanceState(
    serverId: String,
    messageNo: Int,
    instanceId: String,
    state: LifecycleState)
    extends ServerMessage

private[messaging] final case class LaunchCluster(clusterSpec: ClusterSpec) extends ClientMessage

private[messaging] final case class LaunchSpotCluster(
    clusterSpec: ClusterSpec,
    bidPrice: BigDecimal)
    extends ClientMessage

private[messaging] final case class TerminateCluster(clusterId: ClusterId) extends ClientMessage

private[messaging] final case class TerminateWorker(instanceId: String) extends ClientMessage

private[messaging] final case class WorkerAdditionAttempt(
    serverId: String,
    messageNo: Int,
    clusterId: ClusterId,
    count: Int,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class WorkerTerminationAttempt(
    serverId: String,
    messageNo: Int,
    instanceId: String,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class WorkersAdded(
    serverId: String,
    messageNo: Int,
    clusterId: ClusterId,
    workers: List[InstanceSnapshot])
    extends ServerMessage

private[messaging] final case class WorkersRemoved(
    serverId: String,
    messageNo: Int,
    clusterId: ClusterId,
    workerIds: List[String])
    extends ServerMessage

private[messaging] object MessageCodec {
  import io.sphere.json._, generic._
  import org.json4s._, jackson._

  private implicit val messageJson = new JSON[Message] {
    private val json = deriveJSON[Message]

    def read(jValue: JValue) =
      json.read(jValue.transformField {
        case ("$type", x) => ("type", x)
      })

    def write(value: Message) =
      json.write(value).transformField {
        case ("type", x) => ("$type", x)
      }
  }

  def encode(message: Message): String = encode(message, pretty = false)

  def encode(message: Message, pretty: Boolean): String =
    if (pretty) {
      prettyJson(toJValue(message))
    } else {
      compactJson(toJValue(message))
    }

  def decode(messageText: String): MessageValidation =
    fromJSON[Message](messageText).leftMap(_.map(_.toString))
}
