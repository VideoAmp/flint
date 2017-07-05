package flint
package service

import scala.concurrent.Future

trait ClusterService {
  val clusterSystem: ClusterSystem

  val instanceSpecs: Seq[InstanceSpecs]

  val subnets: Seq[Subnet]

  val managementService: ManagementService

  def getPlacementGroups(): Future[Seq[String]]

  def getSpotPrices(instanceTypes: String*): Future[Seq[SpotPrice]]

  def launchCluster(spec: ClusterSpec): Future[ManagedCluster]

  def launchSpotCluster(spec: ClusterSpec, workerBidPrice: BigDecimal): Future[ManagedCluster]
}
