package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }, ExecutionContext.Implicits.global

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._

private[aws] class Ec2Client(awsEc2Client: AmazonEC2Async) {
  def cancelSpotInstanceRequests(request: CancelSpotInstanceRequestsRequest): Future[Unit] = {
    val handler =
      new AwsRequestHandler[CancelSpotInstanceRequestsRequest, CancelSpotInstanceRequestsResult]
    awsEc2Client.cancelSpotInstanceRequestsAsync(request, handler)
    handler.future.map(_ => ())
  }

  def createTags(request: CreateTagsRequest): Future[Unit] = {
    val handler = new AwsRequestHandler[CreateTagsRequest, CreateTagsResult]
    awsEc2Client.createTagsAsync(request, handler)
    handler.future.map(_ => ())
  }

  def describeInstances(request: DescribeInstancesRequest): Future[Seq[Reservation]] = {
    val handler = new AwsRequestHandler[DescribeInstancesRequest, DescribeInstancesResult]
    awsEc2Client.describeInstancesAsync(request, handler)
    handler.future.map(_.getReservations.asScala.toIndexedSeq)
  }

  def describeSpotInstanceRequests(
      request: DescribeSpotInstanceRequestsRequest): Future[Seq[SpotInstanceRequest]] = {
    val handler = new AwsRequestHandler[ // scalastyle:ignore
      DescribeSpotInstanceRequestsRequest,
      DescribeSpotInstanceRequestsResult]
    awsEc2Client.describeSpotInstanceRequestsAsync(request, handler)
    handler.future.map(_.getSpotInstanceRequests.asScala.toIndexedSeq)
  }

  def requestSpotInstances(
      request: RequestSpotInstancesRequest): Future[Seq[SpotInstanceRequest]] = {
    val handler = new AwsRequestHandler[RequestSpotInstancesRequest, RequestSpotInstancesResult]
    awsEc2Client.requestSpotInstancesAsync(request, handler)
    handler.future.map(_.getSpotInstanceRequests.asScala.toIndexedSeq)
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
