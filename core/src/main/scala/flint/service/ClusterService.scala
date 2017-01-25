package flint
package service

import scala.concurrent.Future

trait ClusterService {
  val clusterSystem: ClusterSystem

  val instanceSpecs: Seq[InstanceSpecs]

  val managementService: ManagementService

  def getSpotPrices(instanceTypes: String*): Future[Seq[SpotPrice]]

  def launchCluster(spec: ClusterSpec): Future[ManagedCluster]

  def launchSpotCluster(spec: ClusterSpec, workerBidPrice: BigDecimal): Future[ManagedCluster]
}
