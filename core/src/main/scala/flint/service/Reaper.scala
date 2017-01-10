package flint
package service

import java.io.IOException
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.io.Source.fromInputStream
import scala.util.Try

import com.typesafe.scalalogging.LazyLogging

import rx.{ Ctx, Rx }

class Reaper(clusters: Rx[Map[ClusterId, ManagedCluster]])(implicit ctxOwner: Ctx.Owner)
  extends LazyLogging {
  private val reap = new Runnable {
    override def run(): Unit =
      Future {
        clusters.now.values.foreach { managedCluster =>
          managedCluster.cluster.ttl foreach { ttl =>
            val clusterLaunchedAt = managedCluster.cluster.launchedAt
            if (Instant.now.isAfter(clusterLaunchedAt plus(ttl.toSeconds, ChronoUnit.SECONDS))
              && !hasRunningApps(managedCluster.cluster)) {
              managedCluster.terminate()
            }
          }
        }
      }
  }

  private def hasRunningApps(cluster: Cluster): Boolean = {
    val masterUIHost      = cluster.master.ipAddress
    val masterUIPort      = 8080
    val masterUrl         = new URL(s"http://$masterUIHost:$masterUIPort")
    val applicationsRegex = """Applications:</strong>((?s).*)<a href="#running-app">Running""".r

    try {
      val response                 = getContent(masterUrl)
      val matches                  = applicationsRegex.findAllIn(response)
      val runningApplicationsCount = matches.group(1).trim().toInt
      runningApplicationsCount != 0
    } catch {
      case e: IOException =>
        logger.error(s"Failed to fetch Master UI at $masterUrl", e)
        false
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

  private val scheduler = Executors.newSingleThreadScheduledExecutor(
    flintThreadFactory("reaper-interval-thread")
  )

  Try(reap.run())
  val reapInterval = new FiniteDuration(1, java.util.concurrent.TimeUnit.MINUTES)
  scheduler.scheduleWithFixedDelay(reap, 0, reapInterval.length, reapInterval.unit)
}
