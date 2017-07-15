package flint
package service
package aws

import scala.collection.JavaConverters._
import scala.concurrent.Future

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model.{ Subnet => AwsSubnet, _ }

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

  def describePlacementGroups(
      request: DescribePlacementGroupsRequest,
      retries: Int = 3): Future[Seq[String]] = retryFuture(retries) {
    val handler =
      new AwsRequestHandler[DescribePlacementGroupsRequest, DescribePlacementGroupsResult]
    awsEc2Client.describePlacementGroupsAsync(request, handler)
    handler.future.map(
      _.getPlacementGroups.asScala
        .filter(_.getState == "available")
        .map(_.getGroupName)
        .toIndexedSeq)
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

  def describeSpotPriceHistory(
      request: DescribeSpotPriceHistoryRequest,
      retries: Int = 3): Future[Seq[SpotPrice]] = retryFuture(retries) {
    val handler =
      new AwsRequestHandler[DescribeSpotPriceHistoryRequest, DescribeSpotPriceHistoryResult]
    awsEc2Client.describeSpotPriceHistoryAsync(request, handler)
    handler.future.map(_.getSpotPriceHistory.asScala.toIndexedSeq)
  }

  def describeSubnets(request: DescribeSubnetsRequest, retries: Int = 3): Future[Seq[AwsSubnet]] =
    retryFuture(retries) {
      val handler =
        new AwsRequestHandler[DescribeSubnetsRequest, DescribeSubnetsResult]
      awsEc2Client.describeSubnetsAsync(request, handler)
      handler.future.map(_.getSubnets.asScala.toIndexedSeq)
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
