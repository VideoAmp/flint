package flint
package service
package aws

import flint.{ DockerImage => FDockerImage }

import java.time.Duration

import scala.collection.JavaConverters._

import com.amazonaws.services.ec2.model.{ Instance => AwsInstance, Tag }

private[aws] object Tags {
  val ClusterId          = "flint_cluster_id"
  val ClusterTTL         = "flint_cluster_ttl"
  val ClusterIdleTimeout = "flint_cluster_idle_timeout"
  val DockerImage        = "flint_docker_image"
  val Owner              = "flint_owner"
  val SparkRole          = "flint_spark_cluster_role"

  private val LegacyClusterId   = "cluster_id"
  private val LegacyClusterTTL  = "lifetime_hours"
  private val LegacyDockerImage = "docker_image"
  private val LegacyName        = "Name"

  def instanceTags(
      clusterSpec: ClusterSpec,
      role: SparkClusterRole,
      includeLegacyTags: Boolean): Seq[Tag] = {
    val noTags = Map.empty[String, String]

    val commonTags = Map(
      ClusterId   -> clusterSpec.id.toString,
      Owner       -> clusterSpec.owner,
      SparkRole   -> role.name,
      DockerImage -> clusterSpec.dockerImage.canonicalName)

    val ttlTags =
      clusterSpec.ttl.map(ttl => Map(ClusterTTL -> s"${ttl.toHours}h")).getOrElse(noTags)
    val idleTimeoutTags =
      clusterSpec.idleTimeout
        .map(idleTimeout => Map(ClusterIdleTimeout -> s"${idleTimeout.toMinutes}m"))
        .getOrElse(noTags)

    val legacyTags = if (includeLegacyTags) {
      Map(
        LegacyClusterId   -> clusterSpec.id.toString,
        LegacyName        -> s"Flint Spark ${role.name} : ${clusterSpec.owner}",
        LegacyClusterTTL  -> clusterSpec.ttl.map(_.toHours).getOrElse(1L).toString,
        LegacyDockerImage -> clusterSpec.dockerImage.tag)
    } else { noTags }

    (commonTags ++ ttlTags ++ idleTimeoutTags ++ legacyTags).map {
      case (name, value) => new Tag(name, value)
    }.toIndexedSeq
  }

  def findMaster(clusterId: ClusterId, instances: Seq[AwsInstance]): Option[AwsInstance] =
    instances
      .find(
        _.getTags.asScala
          .find(_.getKey == SparkRole)
          .map(_.getValue == SparkClusterRole.Master.name)
          .getOrElse(false))
      .find(
        _.getTags.asScala
          .find(_.getKey == ClusterId)
          .map(_.getValue == clusterId.toString)
          .getOrElse(false))

  def filterWorkers(clusterId: ClusterId, instances: Seq[AwsInstance]): Seq[AwsInstance] =
    instances
      .filter(
        _.getTags.asScala
          .find(_.getKey == SparkRole)
          .map(_.getValue == SparkClusterRole.Worker.name)
          .getOrElse(false))
      .filter(
        _.getTags.asScala
          .find(_.getKey == ClusterId)
          .map(_.getValue == clusterId.toString)
          .getOrElse(false))

  def getDockerImage(instance: AwsInstance): Option[FDockerImage] =
    getTag(instance, DockerImage).map(FDockerImage(_))

  def getOwner(instance: AwsInstance): Option[String] = getTag(instance, Owner)

  def getClusterTTL(instance: AwsInstance): Option[Duration] =
    getTag(instance, ClusterTTL).map(_.takeWhile(_ != 'h')).map(_.toLong).map(Duration.ofHours)

  def getClusterIdleTimeout(instance: AwsInstance): Option[Duration] =
    getTag(instance, ClusterIdleTimeout)
      .map(_.takeWhile(_ != 'm'))
      .map(_.toLong)
      .map(Duration.ofMinutes)

  private def getTag(instance: AwsInstance, tag: String): Option[String] =
    instance.getTags.asScala.find(_.getKey == tag).map(_.getValue)
}
