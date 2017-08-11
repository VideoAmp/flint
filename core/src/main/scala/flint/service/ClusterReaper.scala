package flint
package service

import ClusterTerminationReason._, ContainerState._

import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.collection.JavaConverters._

import com.fasterxml.jackson.databind.{ JsonNode, ObjectMapper }
import com.typesafe.scalalogging.LazyLogging

import rx.Rx

class ClusterReaper(clusters: Rx[Map[ClusterId, ManagedCluster]])
    extends Runnable
    with LazyLogging {
  import ClusterReaper._

  override def run(): Unit =
    clusters.now.values.foreach { managedCluster =>
      hasTimedOut(managedCluster.cluster).foreach { reason =>
        logger.debug(s"Terminating cluster ${managedCluster.cluster.id} because $reason")
        managedCluster.terminate(reason).foreach(identity)
      }
    }
}

object ClusterReaper extends LazyLogging {
  private val mapper = new ObjectMapper

  def hasTimedOut(cluster: Cluster): Option[ClusterTerminationReason] =
    cluster.master.ipAddress.now.flatMap { masterIpAddress =>
      if (cluster.master.effectiveContainerState.now == ContainerRunning) {
        val masterUIHost  = masterIpAddress
        val masterUIPort  = 8080
        val masterInfoUrl = new URL(s"http://${masterUIHost.getHostAddress}:$masterUIPort/json/")

        val masterInfo = try {
          Some(mapper.readTree(masterInfoUrl))
        } catch {
          case e: IOException =>
            logger.error(s"Failed to fetch master info at $masterInfoUrl", e)
            None
        }

        masterInfo.flatMap { masterInfo =>
          if (isIdle(masterInfo)) {
            checkTtl(cluster).orElse(checkIdleTimeout(cluster, masterInfo))
          } else {
            None
          }
        }
      } else {
        None
      }
    }

  private def isIdle(masterInfo: JsonNode) =
    masterInfo.get("activeapps").size == 0

  private def checkTtl(cluster: Cluster): Option[ClusterTerminationReason] =
    cluster.ttl.map { ttl =>
      val clusterLaunchedAt = cluster.launchedAt
      Instant.now.isAfter(clusterLaunchedAt.plus(ttl.toSeconds, ChronoUnit.SECONDS))
    }.filter(identity).map(_ => TTLExpired)

  private def checkIdleTimeout(
      cluster: Cluster,
      masterInfo: JsonNode): Option[ClusterTerminationReason] =
    cluster.idleTimeout.map { idleTimeout =>
      val completedApps = masterInfo.get("completedapps").asScala

      val lastActivity = if (completedApps.nonEmpty) {
        Instant.ofEpochMilli(
          completedApps
            .map(appInfo => appInfo.get("starttime").longValue + appInfo.get("duration").longValue)
            .max)
      } else {
        cluster.launchedAt
      }

      Instant.now.isAfter(lastActivity.plus(idleTimeout.toSeconds, ChronoUnit.SECONDS))
    }.filter(identity).map(_ => IdleTimeout)
}
