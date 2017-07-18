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
      executionTimeout: FiniteDuration): Future[Seq[Instance]] =
    if (instances.nonEmpty) {
      val sendCommandRequest =
        new SendCommandRequest()
          .withDocumentName("AWS-RunShellScript")
          .withInstanceIds(instances.map(_.id).asJava)
          .withComment(comment.take(100))
          .withParameters(
            Map("commands" -> command, "executionTimeout" -> executionTimeout.toSeconds.toString)
              .mapValues(value => Seq(value).asJava)
              .asJava)

      ssmClient.sendCommand(sendCommandRequest).map(waitForCommandCompletion(_, instances))
    } else {
      Future.successful(Nil)
    }

  @annotation.tailrec
  private def waitForCommandCompletion(command: Command, instances: Seq[Instance]): Seq[Instance] = {
    val commandId = command.getCommandId
    val listCommandInvocationsRequest =
      new ListCommandInvocationsRequest().withCommandId(commandId)
    val commandInvocationInstanceIds =
      Await
        .ready(ssmClient.listCommandInvocations(listCommandInvocationsRequest), Duration("5 min"))
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

    val completedInvocationInstanceIds = commandInvocationInstanceIds.filter(_.isRight)
    val otherInvocationInstanceIds     = commandInvocationInstanceIds.filter(_.isLeft)

    if ((commandInvocationInstanceIds.size - otherInvocationInstanceIds.size) != instances.size) {
      Thread.sleep(5000)
      waitForCommandCompletion(command, instances)
    } else {
      val successfulInvocationInstanceIds =
        completedInvocationInstanceIds
          .flatMap(_.right.toOption)
          .collect { case Some(id) => id }
          .toSet
      instances.filter(instance => successfulInvocationInstanceIds.contains(instance.id))
    }
  }
}
