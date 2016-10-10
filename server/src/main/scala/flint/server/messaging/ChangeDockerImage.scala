package flint
package server
package messaging

private[messaging] case class ChangeDockerImage(clusterId: ClusterId, dockerImage: DockerImage)
    extends ClientMessage
