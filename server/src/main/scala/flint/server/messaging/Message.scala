package flint
package server
package messaging

import service.ClusterSpec

private[messaging] sealed trait Message

private[messaging] sealed trait ClientMessage extends Message

private[messaging] sealed trait ServerMessage extends Message {
  val id: Int
  val error: Option[String]
}

private[messaging] sealed trait TerminationReason

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
    id: Int,
    clusterSpec: ClusterSpec,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class ClusterTerminationAttempt(
    id: Int,
    clusterId: ClusterId,
    reason: TerminationReason,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class DockerImageChangeAttempt(
    id: Int,
    clusterId: ClusterId,
    dockerImage: DockerImage,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class LaunchCluster(clusterSpec: ClusterSpec) extends ClientMessage

private[messaging] final case class TerminateCluster(clusterId: ClusterId) extends ClientMessage

private[messaging] final case class TerminateWorker(instanceId: String) extends ClientMessage

private[messaging] final case class WorkerAdditionAttempt(
    id: Int,
    clusterId: ClusterId,
    count: Int,
    error: Option[String])
    extends ServerMessage

private[messaging] final case class WorkerTerminationAttempt(
    id: Int,
    instanceId: String,
    reason: TerminationReason,
    error: Option[String])
    extends ServerMessage

private[messaging] object MessageCodec {
  import java.time.Duration
  import io.sphere.json._, generic._
  import org.json4s._, jackson._
  import scalaz.Success

  private implicit val jsonDuration: JSON[Duration] = new JSON[Duration] {
    override def read(jval: JValue): JValidation[Duration] = jval match {
      case JString(s) =>
        try {
          Success(Duration.parse(s))
        } catch {
          case ex: Exception => fail("Failed to parse duration: " + ex.getMessage)
        }
      case _ => fail("String expected")
    }

    override def write(value: Duration): JValue = JString(value.toString)
  }

  private implicit val dockerImageJson = deriveJSON[DockerImage]

  private implicit val terminationReasonJson = new JSON[TerminationReason] {
    override def read(jval: JValue): JValidation[TerminationReason] = jval match {
      case JString("ClientRequested") => Success(ClientRequested)
      case JString("IdleTimeout")     => Success(IdleTimeout)
      case JString("TTLExpired")      => Success(TTLExpired)
      case JString(x)                 => fail("Invalid termination reason: " + x)
      case _ =>
        fail("Termination reason must be a string expected")
    }

    override def write(value: TerminationReason): JValue = JString(value.toString)
  }

  private implicit val clusterSpecJson = deriveJSON[ClusterSpec]

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

  def decode[M <: Message](messageText: String): MessageValidation[M] =
    fromJSON[Message](messageText).leftMap(_.map(_.toString)).map(_.asInstanceOf[M])
}
