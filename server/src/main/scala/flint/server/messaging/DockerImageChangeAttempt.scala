package flint
package server
package messaging

private[messaging] case class DockerImageChangeAttempt(
    id: Int,
    clusterId: ClusterId,
    dockerImage: DockerImage,
    error: Option[String])
    extends ServerMessage
