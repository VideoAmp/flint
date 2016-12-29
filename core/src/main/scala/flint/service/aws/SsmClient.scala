package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }, ExecutionContext.Implicits.global

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.amazonaws.services.simplesystemsmanagement.model._

private[aws] class SsmClient(awsSsmClient: AWSSimpleSystemsManagementAsync) {
  def listCommandInvocations(
      request: ListCommandInvocationsRequest): Future[Seq[CommandInvocation]] = {
    val handler =
      new AwsRequestHandler[ListCommandInvocationsRequest, ListCommandInvocationsResult]
    awsSsmClient.listCommandInvocationsAsync(request, handler)
    handler.future.map(_.getCommandInvocations.asScala.toIndexedSeq)
  }

  def sendCommand(request: SendCommandRequest): Future[Command] = {
    val handler = new AwsRequestHandler[SendCommandRequest, SendCommandResult]
    awsSsmClient.sendCommandAsync(request, handler)
    handler.future.map(_.getCommand)
  }
}
