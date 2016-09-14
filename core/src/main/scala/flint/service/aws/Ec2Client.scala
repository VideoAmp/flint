package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.{ Future, Promise }

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._

private[aws] class Ec2Client(awsEc2Client: AmazonEC2Async) {
  import Ec2Client.AwsHandler

  def createTags(request: CreateTagsRequest): Future[Unit] = {
    val handler = new AwsHandler[CreateTagsRequest, CreateTagsResult]
    awsEc2Client.createTagsAsync(request, handler)
    handler.future.map(_ => ())
  }

  def describeInstanceStatus(
      request: DescribeInstanceStatusRequest): Future[Seq[InstanceStatus]] = {
    val handler = new AwsHandler[DescribeInstanceStatusRequest, DescribeInstanceStatusResult]
    awsEc2Client.describeInstanceStatusAsync(request, handler)
    handler.future.map(_.getInstanceStatuses.asScala.toIndexedSeq)
  }

  def runInstances(request: RunInstancesRequest): Future[Reservation] = {
    val handler = new AwsHandler[RunInstancesRequest, RunInstancesResult]
    awsEc2Client.runInstancesAsync(request, handler)
    handler.future.map(_.getReservation)
  }

  def terminateInstances(request: TerminateInstancesRequest): Future[Seq[InstanceStateChange]] = {
    val handler = new AwsHandler[TerminateInstancesRequest, TerminateInstancesResult]
    awsEc2Client.terminateInstancesAsync(request, handler)
    handler.future.map(_.getTerminatingInstances.asScala.toIndexedSeq)
  }
}

private object Ec2Client {
  private class AwsHandler[Req <: AmazonWebServiceRequest, Res] extends AsyncHandler[Req, Res] {
    private val promise = Promise[Res]

    val future = promise.future

    override def onError(ex: Exception) = promise.failure(ex)

    override def onSuccess(request: Req, result: Res) = promise.success(result)
  }
}
