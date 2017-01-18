package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.Future

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._

private[aws] class Ec2Client(awsEc2Client: AmazonEC2Async) {
  import ConcurrencyUtils.retryFuture
  import ConcurrentIoImplicits._

  def cancelSpotInstanceRequests(
      request: CancelSpotInstanceRequestsRequest,
      retries: Int = 3): Future[Unit] = retryFuture(retries) {
    val handler =
      new AwsRequestHandler[CancelSpotInstanceRequestsRequest, CancelSpotInstanceRequestsResult]
    awsEc2Client.cancelSpotInstanceRequestsAsync(request, handler)
    handler.future.map(_ => ())
  }

  def createTags(request: CreateTagsRequest, retries: Int = 3): Future[Unit] =
    retryFuture(retries) {
      val handler = new AwsRequestHandler[CreateTagsRequest, CreateTagsResult]
      awsEc2Client.createTagsAsync(request, handler)
      handler.future.map(_ => ())
    }

  def describeInstances(
      request: DescribeInstancesRequest,
      retries: Int = 3): Future[Seq[Reservation]] = retryFuture(retries) {
    val handler = new AwsRequestHandler[DescribeInstancesRequest, DescribeInstancesResult]
    awsEc2Client.describeInstancesAsync(request, handler)
    handler.future.map(_.getReservations.asScala.toIndexedSeq)
  }

  def describeSpotInstanceRequests(
      request: DescribeSpotInstanceRequestsRequest,
      retries: Int = 3): Future[Seq[SpotInstanceRequest]] = retryFuture(retries) {
    val handler = new AwsRequestHandler[ // scalastyle:ignore
      DescribeSpotInstanceRequestsRequest,
      DescribeSpotInstanceRequestsResult]
    awsEc2Client.describeSpotInstanceRequestsAsync(request, handler)
    handler.future.map(_.getSpotInstanceRequests.asScala.toIndexedSeq)
  }

  def requestSpotInstances(
      request: RequestSpotInstancesRequest,
      retries: Int = 3): Future[Seq[SpotInstanceRequest]] = retryFuture(retries) {
    val handler = new AwsRequestHandler[RequestSpotInstancesRequest, RequestSpotInstancesResult]
    awsEc2Client.requestSpotInstancesAsync(request, handler)
    handler.future.map(_.getSpotInstanceRequests.asScala.toIndexedSeq)
  }

  def runInstances(request: RunInstancesRequest, retries: Int = 3): Future[Reservation] =
    retryFuture(retries) {
      val handler = new AwsRequestHandler[RunInstancesRequest, RunInstancesResult]
      awsEc2Client.runInstancesAsync(request, handler)
      handler.future.map(_.getReservation)
    }

  def terminateInstances(
      request: TerminateInstancesRequest,
      retries: Int = 3): Future[Seq[InstanceStateChange]] = retryFuture(retries) {
    val handler = new AwsRequestHandler[TerminateInstancesRequest, TerminateInstancesResult]
    awsEc2Client.terminateInstancesAsync(request, handler)
    handler.future.map(_.getTerminatingInstances.asScala.toIndexedSeq)
  }
}
