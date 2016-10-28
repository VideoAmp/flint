package flint
package service

import scala.concurrent.Future

trait ClusterService {
  val clusterSystem: ClusterSystem

  val managementService: ManagementService

  def launchCluster(spec: ClusterSpec): Future[ManagedCluster]
}
