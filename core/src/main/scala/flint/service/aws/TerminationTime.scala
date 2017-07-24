package flint
package service
package aws

import java.time.{ Instant, ZonedDateTime }
import java.time.format.DateTimeFormatter

import com.amazonaws.services.ec2.model.{ Instance => AwsInstance }
import com.typesafe.scalalogging.LazyLogging

private[aws] object TerminationTime extends LazyLogging {
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

  def unapply(awsInstance: AwsInstance): Option[Instant] =
    if (instanceState2LifecycleState(awsInstance.getState) == Terminated) {
      Some(extractInstantFromStateTransitionReason(awsInstance))
    } else {
      None
    }

  private def extractInstantFromStateTransitionReason(awsInstance: AwsInstance): Instant = {
    val reason = awsInstance.getStateTransitionReason

    try {
      val unparsedDateTime = reason.dropRight(1).dropWhile(_ != '(').drop(1)
      val zonedDateTime = ZonedDateTime.parse(unparsedDateTime, dateTimeFormatter)
      Instant.from(zonedDateTime)
    } catch {
      case ex: Exception =>
        logger.error(
          s"Failed to parse timestamp from state transition reason for instance " +
            s"${awsInstance.getInstanceId}: $reason")
        throw ex
    }
  }
}
