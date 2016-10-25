package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.{ Failure, Left, Right, Success }

import com.amazonaws.services.simplesystemsmanagement.model._
import com.typesafe.scalalogging.LazyLogging

private[aws] class AwsManagementService(ssmClient: SsmClient)
    extends ManagementService
    with LazyLogging {
  def sendCommand(
      instances: Seq[Instance],
      comment: String,
      command: String,
      executionTimeout: FiniteDuration): Future[Seq[Instance]] = {
    val sendCommandRequest =
      new SendCommandRequest()
        .withDocumentName("AWS-RunShellScript")
        .withInstanceIds(instances.map(_.id).asJava)
        .withComment(comment)
        .withParameters(
          Map("commands" -> command, "executionTimeout" -> executionTimeout.toSeconds.toString)
            .mapValues(value => Seq(value).asJava)
            .asJava)

    ssmClient.sendCommand(sendCommandRequest).map { command =>
      val commandId = command.getCommandId
      val listCommandInvocationsRequest =
        new ListCommandInvocationsRequest().withCommandId(commandId)

      @annotation.tailrec
      def waitForCommandCompletion(): Seq[Instance] = {
        val instanceIds =
          Await
            .ready(
              ssmClient.listCommandInvocations(listCommandInvocationsRequest),
              Duration("5 min"))
            .value
            .get match {
            case Success(invocations) =>
              invocations.map { invocation =>
                val instanceId = invocation.getInstanceId
                CommandInvocationStatus.fromValue(invocation.getStatus) match {
                  case CommandInvocationStatus.Success =>
                    Right(Some(instanceId))
                  case CommandInvocationStatus.Cancelled | CommandInvocationStatus.Cancelling |
                      CommandInvocationStatus.Failed | CommandInvocationStatus.TimedOut =>
                    logger.error(
                      invocation.getComment + " for instance " + instanceId + " status: " +
                        invocation.getStatus)
                    Right(None)
                  case _ => Left(Some(instanceId))
                }
              }
            case Failure(ex) =>
              throw new RuntimeException(
                "Caught exception while listing invocations for " + command.getComment +
                  " command id " + commandId,
                ex)
          }

        val pendingInstances = instanceIds.filter(_.isLeft).nonEmpty

        if (pendingInstances) {
          Thread.sleep(5000)
          waitForCommandCompletion
        } else {
          val successfulInstanceIds =
            instanceIds.flatMap(_.right.toOption).collect { case Some(id) => id }.toSet
          instances.filter(instance => successfulInstanceIds.contains(instance.id))
        }
      }

      waitForCommandCompletion
    }
  }

}
