package flint
package server
package messaging

import service.ClusterSpec

private[server] case class LaunchCluster(spec: ClusterSpec) extends ClientMessage
