package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.Future

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.amazonaws.services.simplesystemsmanagement.model._

private[aws] class SsmClient(awsSsmClient: AWSSimpleSystemsManagementAsync) {
  import ConcurrencyUtils.retryFuture
  import ConcurrentIoImplicits._

  def listCommandInvocations(
      request: ListCommandInvocationsRequest,
      retries: Int = 3): Future[Seq[CommandInvocation]] = retryFuture(retries) {
    val handler =
      new AwsRequestHandler[ListCommandInvocationsRequest, ListCommandInvocationsResult]
    awsSsmClient.listCommandInvocationsAsync(request, handler)
    handler.future.map(_.getCommandInvocations.asScala.toIndexedSeq)
  }

  def sendCommand(request: SendCommandRequest, retries: Int = 3): Future[Command] =
    retryFuture(retries) {
      val handler = new AwsRequestHandler[SendCommandRequest, SendCommandResult]
      awsSsmClient.sendCommandAsync(request, handler)
      handler.future.map(_.getCommand)
    }
}
