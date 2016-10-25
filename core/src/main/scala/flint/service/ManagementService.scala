package flint
package service

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait ManagementService {
  def sendCommand(
      instances: Seq[Instance],
      comment: String,
      command: String,
      executionTimeout: FiniteDuration): Future[Seq[Instance]]
}
