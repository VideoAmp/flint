package flint
package server
package messaging

import service.ClusterSpec

private[server] case class ClusterLaunchAttempt(id: Int, spec: ClusterSpec, error: Option[String])
    extends ServerMessage
