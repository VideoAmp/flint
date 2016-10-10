package flint
package server
package messaging

private[messaging] case class WorkerTerminationAttempt(
    id: Int,
    instanceId: String,
    reason: TerminationReason,
    error: Option[String])
    extends ServerMessage
