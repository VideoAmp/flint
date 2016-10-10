package flint
package server
package messaging

private[server] case class TerminateCluster(clusterId: ClusterId) extends ClientMessage
