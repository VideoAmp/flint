package flint
package server
package messaging

private[server] case class TerminateWorker(instanceId: String) extends ClientMessage
