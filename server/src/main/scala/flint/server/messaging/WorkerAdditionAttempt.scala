package flint
package server
package messaging

private[messaging] case class WorkerAdditionAttempt(
    id: Int,
    clusterId: ClusterId,
    count: Int,
    error: Option[String])
    extends ServerMessage
