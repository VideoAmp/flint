package flint
package service
package aws

import flint.{ ContainerState => FContainerState, DockerImage => FDockerImage }

import java.time.Duration

import scala.collection.JavaConverters._

import com.amazonaws.services.ec2.model.{ Instance => AwsInstance, Tag }

private[aws] object Tags {
  val ResourceName = "Name"

  val ClusterId          = "flint_cluster_id"
  val ClusterDockerImage = "flint_cluster_docker_image"
  val ClusterTTL         = "flint_cluster_ttl"
  val ClusterIdleTimeout = "flint_cluster_idle_timeout"
  val ContainerState     = "flint_container_state"
  val DockerImage        = "flint_docker_image"
  val Owner              = "flint_owner"
  val SparkRole          = "flint_spark_cluster_role"
  val WorkerInstanceType = "flint_worker_instance_type"
  val WorkerBidPrice     = "flint_worker_bid_price"

  val LegacyClusterId           = "cluster_id"
  private val LegacyClusterTTL  = "lifetime_hours"
  private val LegacyDockerImage = "docker_image"

  def masterTags(
      clusterSpec: ClusterSpec,
      workerBidPrice: Option[BigDecimal],
      includeLegacyTags: Boolean): Seq[Tag] = {
    import clusterSpec._

    val noTags = Map.empty[String, String]

    val commonResourceTags = commonResourceRequestTags(id, owner, SparkClusterRole.Master)

    val commonTags = Map(
      ClusterDockerImage -> dockerImage.canonicalName,
      WorkerInstanceType -> workerInstanceType)

    val ttlTags =
      ttl.map(ttl => Map(ClusterTTL -> s"${ttl.toHours}h")).getOrElse(noTags)
    val idleTimeoutTags =
      idleTimeout
        .map(idleTimeout => Map(ClusterIdleTimeout -> s"${idleTimeout.toMinutes}m"))
        .getOrElse(noTags)
    val workerBidPriceTags = workerBidPrice
      .map(workerBidPrice => Map(WorkerBidPrice -> workerBidPrice.toString))
      .getOrElse(noTags)

    val legacyTags = if (includeLegacyTags) {
      Map(
        LegacyClusterId   -> id.toString,
        LegacyClusterTTL  -> ttl.map(_.toHours).getOrElse(Int.MaxValue.toLong).toString,
        LegacyDockerImage -> dockerImage.tag)
    } else { noTags }

    commonResourceTags ++
      (commonTags ++ ttlTags ++ idleTimeoutTags ++ workerBidPriceTags ++ legacyTags).map {
        case (name, value) => new Tag(name, value)
      }.toSeq
  }

  def workerTags(clusterId: ClusterId, owner: String, includeLegacyTags: Boolean): Seq[Tag] = {
    val noTags = Map.empty[String, String]

    val commonTags = commonResourceRequestTags(clusterId, owner, SparkClusterRole.Worker)

    val legacyTags = if (includeLegacyTags) {
      Map(LegacyClusterId -> clusterId.toString)
    } else { noTags }

    commonTags ++ legacyTags.map {
      case (name, value) => new Tag(name, value)
    }.toSeq
  }

  def spotInstanceRequestTags(clusterId: ClusterId, owner: String): Seq[Tag] =
    commonResourceRequestTags(clusterId, owner, SparkClusterRole.Worker)

  def commonResourceRequestTags(
      clusterId: ClusterId,
      owner: String,
      role: SparkClusterRole): Seq[Tag] = {
    val commonTags = Map(
      ResourceName -> s"Flint Spark ${role.name} : $owner",
      ClusterId    -> clusterId.toString,
      Owner        -> owner,
      SparkRole    -> role.name)

    commonTags.map {
      case (name, value) => new Tag(name, value)
    }.toIndexedSeq
  }

  def dockerImageTags(dockerImage: DockerImage, includeLegacyTags: Boolean): Seq[Tag] = {
    val tags = Seq(
      ClusterDockerImage -> dockerImage.canonicalName,
      DockerImage        -> dockerImage.canonicalName)
    val legacyTags =
      if (includeLegacyTags) {
        Seq(LegacyDockerImage -> dockerImage.tag)
      } else {
        Seq.empty
      }

    (tags ++ legacyTags).map {
      case (name, value) => new Tag(name, value)
    }
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

  def getContainerState(instance: AwsInstance): Option[ContainerState] =
    getTag(instance, ContainerState).map(FContainerState(_))

  def getClusterDockerImage(instance: AwsInstance): Option[FDockerImage] =
    getTag(instance, ClusterDockerImage).map(FDockerImage(_))

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

  def getWorkerInstanceType(instance: AwsInstance): Option[String] =
    getTag(instance, WorkerInstanceType)

  def getWorkerBidPrice(instance: AwsInstance): Option[BigDecimal] =
    getTag(instance, WorkerBidPrice).map(BigDecimal(_))

  private def getTag(instance: AwsInstance, tag: String): Option[String] =
    instance.getTags.asScala.find(_.getKey == tag).map(_.getValue)
}
