package flint
package service

import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.io.Source.fromInputStream

import com.typesafe.scalalogging.LazyLogging

import rx.{ Ctx, Rx }

class ClusterReaper(clusters: Rx[Map[ClusterId, ManagedCluster]])(implicit ctxOwner: Ctx.Owner)
    extends Runnable {
  import ClusterReaper._

  override def run(): Unit =
    clusters.now.values.foreach { managedCluster =>
      managedCluster.cluster.ttl foreach { ttl =>
        val clusterLaunchedAt = managedCluster.cluster.launchedAt
        if (Instant.now.isAfter(clusterLaunchedAt plus (ttl.toSeconds, ChronoUnit.SECONDS))) {
          hasRunningApps(managedCluster.cluster).filter(_ == false).foreach { _ =>
            managedCluster.terminate()
          }
        }
      }
    }
}

object ClusterReaper extends LazyLogging {
  val MasterUIRunningAppCount =
    """Applications:<\/strong>(?:\s*)(\d+)(?:\s*)<a href="#running-app">Running""".r.unanchored

  def hasRunningApps(cluster: Cluster): Option[Boolean] =
    cluster.master.ipAddress.now.flatMap { masterIpAddress =>
      if (cluster.master.containerState.now == ContainerRunning) {
        val masterUIHost = masterIpAddress
        val masterUIPort = 8080
        val masterUrl    = new URL(s"http://${masterUIHost.getHostAddress}:$masterUIPort")

        try {
          val response                                 = getContent(masterUrl)
          val MasterUIRunningAppCount(runningAppCount) = response.replaceAll("""\R""", "")
          Some(runningAppCount.toInt != 0)
        } catch {
          case e: IOException =>
            logger.error(s"Failed to fetch Master UI at $masterUrl", e)
            None
        }
      } else {
        None
      }
    }

  private def getContent(
      url: URL,
      connectTimeout: Int = 5000,
      readTimeout: Int = 5000
  ): String = {
    val connection = url.openConnection
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    val inputStream = connection.getInputStream
    try {
      fromInputStream(inputStream).mkString
    } finally {
      inputStream.close()
    }
  }
}
