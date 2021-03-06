package flint
package service
package aws

import java.util.concurrent.{ Executors, ScheduledExecutorService }

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

  private val clustersRebuildTask = new Runnable {
    override def run(): Unit =
      Await.ready(awsClusterService.describeFlintInstances, Duration.Inf).onComplete {
        case Success(reservations) =>
          // A map from cluster id to all Flint cluster instances currently known to AWS
          val currentClusterInstances = reservations
            .flatMap(_.getInstances.asScala)
            .map { instance =>
              val optClusterId = instance.getTags.asScala
                .find(_.getKey == FlintTags.ClusterId)
                .map(_.getValue)
                .map(ClusterId(_))
              (optClusterId, instance)
            }
            .collect {
              case (Some(clusterId), instance) => (clusterId, instance)
            }
            .groupBy(_._1)
            .mapValues(_.map(_._2))

          val clustersNow = clusters.now

          // Rebuild `clusters` from `currentClusterInstances` in three steps:
          // 1. Retain clusters present in `currentClusterInstances`
          val (retainedClusters, removedClusters) = clustersNow.partition {
            case (clusterId, _) =>
              currentClusterInstances.contains(clusterId)
          }

          // 2. Update retained clusters
          retainedClusters.foreach {
            case (clusterId, managedCluster) =>
              managedCluster() = currentClusterInstances(clusterId)
          }

          // 3. Create clusters not present in `clusters`
          val newClusters = currentClusterInstances.filterNot {
            case (clusterId, _) => clustersNow.contains(clusterId)
          }.map {
            case (clusterId, instances) =>
              clusterId -> AwsManagedCluster.forInstances(clusterId, instances, awsClusterService)
          }.collect {
            case (clusterId, Some(managedCluster)) => clusterId -> managedCluster
          }.toMap

          if (newClusters.nonEmpty) {
            logger.debug(s"Adding ${newClusters.size} new cluster(s)")
            AwsClusterSystem.this.newClusters.asVar() = newClusters.values.toIndexedSeq
          }

          if (removedClusters.nonEmpty) {
            logger.debug(s"Removing ${removedClusters.size} retired cluster(s)")
            AwsClusterSystem.this.removedClusters.asVar() =
              removedClusters.values.map(_.cluster.id).toIndexedSeq
          }

          clusters() = retainedClusters ++ newClusters
        case Failure(ex) =>
          logger.error("Received exception trying to describe instances", ex)
      }
  }

  private[flint] lazy val refreshExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(
      flintThreadFactory("aws-cluster-system-refresh-thread"))

  {
    val pollingInterval = clustersRefreshConfig.get[FiniteDuration]("polling_interval").value
    refreshExecutor.scheduleWithFixedDelay(
      clustersRebuildTask,
      0,
      pollingInterval.length,
      pollingInterval.unit)
  }

  private[aws] def addCluster(managedCluster: AwsManagedCluster): Unit = {
    val clustersNow = clusters.now
    val clusterId   = managedCluster.cluster.id

    if (!clustersNow.contains(clusterId)) {
      logger.debug("Adding new cluster")
      newClusters.asVar() = Seq(managedCluster)
      clusters() = clustersNow.updated(clusterId, managedCluster)
    }
  }

  private[aws] def updateInstanceState(instanceId: String, newState: InstanceState): Unit =
    clusters.now.values
      .flatMap(_.cluster.instances.now)
      .find(_.id == instanceId)
      .foreach(_.state.asVar() = newState)
}
