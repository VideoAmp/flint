package flint
package server
package messaging

private[messaging] case class ClusterTerminationAttempt(
    id: Int,
    clusterId: ClusterId,
    reason: TerminationReason,
    error: Option[String])
    extends ServerMessage
