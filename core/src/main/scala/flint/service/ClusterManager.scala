package flint
package service

import scala.concurrent.Future

trait ClusterManager {
  def launchCluster(spec: ClusterSpec): Future[Cluster]
}
