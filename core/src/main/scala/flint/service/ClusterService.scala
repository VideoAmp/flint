package flint
package service

import scala.concurrent.Future

import rx._

trait ClusterService {
  def clusters: Rx[Map[ClusterId, ManagedCluster]]

  def launchCluster(spec: ClusterSpec): Future[ManagedCluster]
}
