package flint
package service
package aws

import java.util.concurrent.Executors

import scala.collection.concurrent.TrieMap
import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.{ Failure, Success }

import com.amazonaws.services.ec2.model.{ Instance => _, _ }
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging

import configs.syntax._

import rx._

private[aws] class InstanceStatusPoller(awsConfig: Config, ec2Client: Ec2Client)
    extends LazyLogging {
  private val instanceLifecycleStateMap = new TrieMap[String, Var[LifecycleState]]

  private val scheduler = Executors.newSingleThreadScheduledExecutor(flintThreadFactory)

  private val task = new Runnable {
    override def run() = {
      val instanceIds = instanceLifecycleStateMap.keys.toSeq

      if (instanceIds.nonEmpty) {
        val request =
          new DescribeInstanceStatusRequest().withInstanceIds(instanceIds: _*)
        Await.ready(ec2Client.describeInstanceStatus(request), Duration.Inf).onComplete {
          case Success(statuses) =>
            statuses.map { status =>
              val instanceId = status.getInstanceId
              instanceLifecycleStateMap.get(instanceId).foreach(_() = status.getInstanceState)
            }
          case Failure(ex) =>
            logger.error("Received exception trying to get instance statuses", ex)
        }
      }
    }
  }

  {
    val pollingInterval = awsConfig.get[FiniteDuration]("status_polling_interval").value
    scheduler.scheduleWithFixedDelay(task, 0, pollingInterval.length, pollingInterval.unit)
  }

  private[aws] def getOrCreateInstanceLifecycleState(instanceId: String): Rx[LifecycleState] =
    instanceLifecycleStateMap.getOrElseUpdate(instanceId, new Var[LifecycleState](Pending))

  private[aws] def updateInstanceLifecycleState(
      instanceId: String,
      newLifecycleState: LifecycleState): Unit = {
    instanceLifecycleStateMap.get(instanceId).foreach(_() = newLifecycleState)
  }
}
