package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.Future

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._

private[aws] class Ec2Client(awsEc2Client: AmazonEC2Async) {
  def createTags(request: CreateTagsRequest): Future[Unit] = {
    val handler = new AwsRequestHandler[CreateTagsRequest, CreateTagsResult]
    awsEc2Client.createTagsAsync(request, handler)
    handler.future.map(_ => ())
  }

  def describeInstanceStatus(
      request: DescribeInstanceStatusRequest): Future[Seq[InstanceStatus]] = {
    val handler =
      new AwsRequestHandler[DescribeInstanceStatusRequest, DescribeInstanceStatusResult]
    awsEc2Client.describeInstanceStatusAsync(request, handler)
    handler.future.map(_.getInstanceStatuses.asScala.toIndexedSeq)
  }

  def describeInstances(request: DescribeInstancesRequest): Future[Seq[Reservation]] = {
    val handler = new AwsRequestHandler[DescribeInstancesRequest, DescribeInstancesResult]
    awsEc2Client.describeInstancesAsync(request, handler)
    handler.future.map(_.getReservations.asScala.toIndexedSeq)
  }

  def runInstances(request: RunInstancesRequest): Future[Reservation] = {
    val handler = new AwsRequestHandler[RunInstancesRequest, RunInstancesResult]
    awsEc2Client.runInstancesAsync(request, handler)
    handler.future.map(_.getReservation)
  }

  def terminateInstances(request: TerminateInstancesRequest): Future[Seq[InstanceStateChange]] = {
    val handler = new AwsRequestHandler[TerminateInstancesRequest, TerminateInstancesResult]
    awsEc2Client.terminateInstancesAsync(request, handler)
    handler.future.map(_.getTerminatingInstances.asScala.toIndexedSeq)
  }
}
