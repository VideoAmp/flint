package flint
package service

import java.io.IOException
import java.net.{ HttpURLConnection, URL }
import java.time.Instant
import java.util.concurrent.Executors

import scala.concurrent.duration.FiniteDuration
import scala.io.Source.fromInputStream
import scala.util.Try

import rx.{ Ctx, Rx }

class Reaper(clusters: Rx[Map[ClusterId, ManagedCluster]])(implicit ctxOwner: Ctx.Owner) {
  private val reap = new Runnable {
    override def run(): Unit =
      clusters.now.values.foreach { managedCluster =>
        managedCluster.cluster.ttl foreach { ttl =>
          val clusterLaunchedAt = managedCluster.cluster.launchedAt
          if (Instant.now.isAfter(clusterLaunchedAt plus ttl)
              && clusterIsKillable(managedCluster.cluster)) {
            managedCluster.terminate()
          }
        }
      }
  }

  private def clusterIsKillable(cluster: Cluster): Boolean = {
    val masterIp          = cluster.master.ipAddress
    val masterUIPort      = 8080
    val applicationsRegex = """Applications:</strong>((?s).*)<a href="#running-app">Running""".r

    try {
      val response                 = get(s"http://$masterIp:$masterUIPort")
      val matches                  = applicationsRegex.findAllIn(response)
      val runningApplicationsCount = matches.group(1).trim().toInt
      runningApplicationsCount == 0
    } catch {
      case e: IOException => true
      case _: Throwable   => false
    }
  }

  private def get(url: String, connectTimeout: Int = 5000, readTimeout: Int = 5000): String = {
    val connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(connectTimeout)
    connection.setReadTimeout(readTimeout)
    connection.setRequestMethod("GET")
    val inputStream = connection.getInputStream
    val content     = fromInputStream(inputStream).mkString
    if (inputStream != null) inputStream.close()
    content
  }

  private val scheduler = Executors.newSingleThreadScheduledExecutor(flintThreadFactory)

  Try(reap.run())
  val reapInterval = new FiniteDuration(5, java.util.concurrent.TimeUnit.MINUTES)
  scheduler.scheduleWithFixedDelay(reap, 0, reapInterval.length, reapInterval.unit)
}
