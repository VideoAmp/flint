package flint
package service

import rx._

trait ClusterSystem {
  val clusters: Rx[Map[ClusterId, ManagedCluster]]

  val newCluster: Rx[Option[ManagedCluster]]
}
