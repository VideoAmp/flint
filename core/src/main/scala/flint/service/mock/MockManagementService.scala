package flint
package service
package mock

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

private[mock] object MockManagementService extends ManagementService {
  def sendCommand(
      instances: Seq[Instance],
      comment: String,
      command: String,
      executionTimeout: FiniteDuration): Future[Seq[Instance]] = ???
}
