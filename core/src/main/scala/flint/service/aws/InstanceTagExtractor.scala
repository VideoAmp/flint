package flint
package service
package aws

import flint.{ ContainerState => FContainerState, DockerImage => FDockerImage }
import flint.service.FlintTags.commonResourceRequestTags

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

import com.amazonaws.services.ec2.model.{ Instance => AwsInstance, Tag }

private[aws] object InstanceTagExtractor {
  def spotInstanceRequestTags(clusterId: ClusterId, clusterName: String): Seq[(String, String)] =
    commonResourceRequestTags(clusterId, clusterName, SparkClusterRole.Worker)

  def asAwsTag(tagInfo: (String, String)): Tag = new Tag(tagInfo._1, tagInfo._2)

  def findMaster(clusterId: ClusterId, instances: Seq[AwsInstance]): Option[AwsInstance] =
    instances
      .find(
        _.getTags.asScala
          .find(_.getKey == FlintTags.SparkRole)
          .exists(_.getValue == SparkClusterRole.Master.name))
      .find(
        _.getTags.asScala
          .find(_.getKey == FlintTags.ClusterId)
          .exists(_.getValue == clusterId.toString))

  def filterWorkers(clusterId: ClusterId, instances: Seq[AwsInstance]): Seq[AwsInstance] =
    instances
      .filter(
        _.getTags.asScala
          .find(_.getKey == FlintTags.SparkRole)
          .exists(_.getValue == SparkClusterRole.Worker.name))
      .filter(
        _.getTags.asScala
          .find(_.getKey == FlintTags.ClusterId)
          .exists(_.getValue == clusterId.toString))

  def getContainerState(instance: AwsInstance): Option[ContainerState] =
    getTag(instance, FlintTags.ContainerState).map(FContainerState(_))

  def getClusterDockerImage(instance: AwsInstance): Option[FDockerImage] =
    getTag(instance, FlintTags.ClusterDockerImage).map(FDockerImage(_))

  def getDockerImage(instance: AwsInstance): Option[FDockerImage] =
    getTag(instance, FlintTags.DockerImage).map(FDockerImage(_))

  def getClusterName(instance: AwsInstance): Option[String] =
    getTag(instance, FlintTags.ClusterName)

  def getClusterTTL(instance: AwsInstance): Option[FiniteDuration] =
    getTag(instance, FlintTags.ClusterTTL)
      .map(_.takeWhile(_ != 'h'))
      .map(_.toLong)
      .map(FiniteDuration(_, "hours"))

  def getClusterIdleTimeout(instance: AwsInstance): Option[FiniteDuration] =
    getTag(instance, FlintTags.ClusterIdleTimeout)
      .map(_.takeWhile(_ != 'm'))
      .map(_.toLong)
      .map(FiniteDuration(_, "minutes"))

  def getWorkerInstanceType(instance: AwsInstance): Option[String] =
    getTag(instance, FlintTags.WorkerInstanceType)

  def getPlacementGroup(instance: AwsInstance): Option[String] =
    getTag(instance, FlintTags.PlacementGroup)

  def getWorkerBidPrice(instance: AwsInstance): Option[BigDecimal] =
    getTag(instance, FlintTags.WorkerBidPrice).map(BigDecimal(_))

  def getExtraInstanceTags(instance: AwsInstance): Map[String, String] =
    instance.getTags.asScala
      .filterNot(t => FlintTags.isFlintTag(t.getKey))
      .map(t => (t.getKey, t.getValue))
      .toMap

  private def getTag(instance: AwsInstance, tag: String): Option[String] =
    instance.getTags.asScala.find(_.getKey == tag).map(_.getValue)
}
