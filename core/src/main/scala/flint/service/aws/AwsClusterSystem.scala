package flint
package service
package aws

import java.util.concurrent.Executors

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.{ Failure, Success }

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import configs.syntax._

import rx._

private[aws] class AwsClusterSystem private[aws] (
    awsClusterService: AwsClusterService,
    clustersRefreshConfig: Config)(implicit protected val ctx: Ctx.Owner)
    extends ClusterSystem
    with LazyLogging {
  override val clusters = Var(Map.empty[ClusterId, AwsManagedCluster])

  override val newClusters = Var(Seq.empty[ManagedCluster])

  private val task = new Runnable {
    override def run(): Unit =
      Await.ready(awsClusterService.describeFlintInstances, Duration.Inf).onComplete {
        case Success(reservations) =>
          val currentClusterInstances = reservations
            .flatMap(_.getInstances.asScala)
            .map { instance =>
              val optClusterId = instance.getTags.asScala
                .find(_.getKey == Tags.ClusterId)
                .map(_.getValue)
                .map(ClusterId(_))
              (optClusterId, instance)
            }
            .collect {
              case (Some(clusterId), instance) => (clusterId, instance)
            }
            .groupBy(_._1)
            .mapValues(_.map(_._2))

          clusters.synchronized {
            val clustersNow = clusters.now

            // Update `clusters` from `currentClusterInstances` in three steps:
            // 1. Retain clusters present in `currentClusterInstances`
            val retainedClusters = clustersNow.filter {
              case (clusterId, _) =>
                currentClusterInstances.contains(clusterId)
            }

            // 2. Update retained clusters
            retainedClusters.foreach {
              case (clusterId, managedCluster) =>
                managedCluster() = currentClusterInstances(clusterId)
            }

            // 3. Create new clusters not present in `clusters`
            val newClusters = currentClusterInstances.filterNot {
              case (clusterId, _) => clustersNow.contains(clusterId)
            }.map {
              case (clusterId, instances) =>
                clusterId -> AwsManagedCluster
                  .forInstances(clusterId, instances, awsClusterService)
            }.collect {
              case (clusterId, Some(managedCluster)) => clusterId -> managedCluster
            }.toMap

            AwsClusterSystem.this.newClusters() = newClusters.values.toIndexedSeq
            clusters() = retainedClusters ++ newClusters
          }
        case Failure(ex) =>
          logger.error("Received exception trying to describe instances", ex)
      }
  }

  private val scheduler = Executors.newSingleThreadScheduledExecutor(flintThreadFactory)

  {
    val pollingInterval = clustersRefreshConfig.get[FiniteDuration]("polling_interval").value
    scheduler.scheduleWithFixedDelay(task, 0, pollingInterval.length, pollingInterval.unit)
  }

  private[aws] def addCluster(managedCluster: AwsManagedCluster): Unit =
    clusters.synchronized {
      val clustersNow = clusters.now
      val clusterId   = managedCluster.cluster.id

      if (!clustersNow.contains(clusterId)) {
        clusters() = clustersNow.updated(clusterId, managedCluster)
      }
    }

  private[aws] def updateInstanceState(instanceId: String, newState: LifecycleState): Unit =
    clusters.now.values
      .flatMap(_.cluster.instances.now)
      .find(_.id == instanceId)
      .foreach(_.state.asVar() = newState)
}
