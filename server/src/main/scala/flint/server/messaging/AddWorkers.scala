package flint
package server
package messaging

private[messaging] case class AddWorkers(clusterId: ClusterId, count: Int) extends ClientMessage
