package flint
package server
package messaging

import service.ClusterSpec

private[messaging] sealed trait Message

private[messaging] sealed trait ClientMessage extends Message

private[messaging] sealed trait ServerMessage extends Message

private[messaging] sealed trait TerminationReason {
  protected val name = toString
}

object TerminationReason {
  def apply(name: String): TerminationReason = name match {
    case ClientRequested.name => ClientRequested
    case IdleTimeout.name     => IdleTimeout
    case TTLExpired.name      => TTLExpired
  }
}

private[messaging] case object ClientRequested extends TerminationReason

private[messaging] case object IdleTimeout extends TerminationReason

private[messaging] case object TTLExpired extends TerminationReason

private[messaging] final case class AddWorkers(clusterId: ClusterId, count: Int)
    extends ClientMessage

private[messaging] final case class ChangeDockerImage(
    clusterId: ClusterId,
    dockerImage: DockerImage)
    extends ClientMessage

private[messaging] final case class ClusterLaunchAttempt(
    clusterSpec: ClusterSpec,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class ClusterTerminationAttempt(
    clusterId: ClusterId,
    reason: TerminationReason,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class DockerImageChangeAttempt(
    clusterId: ClusterId,
    dockerImage: DockerImage,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class InstanceContainerState(
    instanceId: String,
    containerState: ContainerState)
    extends ServerMessage

private[messaging] final case class InstanceDockerImage(
    instanceId: String,
    dockerImage: Option[DockerImage])
    extends ServerMessage

private[messaging] final case class InstanceState(instanceId: String, state: LifecycleState)
    extends ServerMessage

private[messaging] final case class LaunchCluster(clusterSpec: ClusterSpec) extends ClientMessage

private[messaging] final case class TerminateCluster(clusterId: ClusterId) extends ClientMessage

private[messaging] final case class TerminateWorker(instanceId: String) extends ClientMessage

private[messaging] final case class WorkerAdditionAttempt(
    clusterId: ClusterId,
    count: Int,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class WorkerTerminationAttempt(
    instanceId: String,
    reason: TerminationReason,
    error: Option[String])
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
