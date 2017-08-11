package flint.service

import flint.{ ClusterId, DockerImage, Seq, SparkClusterRole }

private[service] class FlintTags(extraTags: ExtraTags) {
  import FlintTags._

  def masterTags(
      clusterSpec: ClusterSpec,
      workerBidPrice: Option[BigDecimal]): Seq[(String, String)] = {
    import clusterSpec._

    val noTags = Map.empty[String, String]

    val commonResourceTags = commonResourceRequestTags(id, name, SparkClusterRole.Master)

    val commonTags = Map(
      ClusterDockerImage -> dockerImage.canonicalName,
      WorkerInstanceType -> workerInstanceType,
      SubnetId           -> subnetId)

    val placementGroupTags = placementGroup
      .map(placementGroup => Map(PlacementGroup -> placementGroup))
      .getOrElse(noTags)

    val ttlTags =
      ttl.map(ttl => Map(ClusterTTL -> s"${ttl.toHours}h")).getOrElse(noTags)
    val idleTimeoutTags =
      idleTimeout
        .map(idleTimeout => Map(ClusterIdleTimeout -> s"${idleTimeout.toMinutes}m"))
        .getOrElse(noTags)
    val workerBidPriceTags = workerBidPrice
      .map(workerBidPrice => Map(WorkerBidPrice -> workerBidPrice.toString))
      .getOrElse(noTags)

    commonResourceTags ++
      (commonTags ++
        placementGroupTags ++
        ttlTags ++
        idleTimeoutTags ++
        workerBidPriceTags ++
        extraTags.tags).toSeq
  }

  def workerTags(clusterId: ClusterId, clusterName: String): Seq[(String, String)] = {
    val commonResourceTags =
      commonResourceRequestTags(clusterId, clusterName, SparkClusterRole.Worker)
    commonResourceTags ++ extraTags.tags.toSeq
  }
}

private[service] object FlintTags {
  val ResourceName = "Name"

  val ClusterId          = "flint:cluster_id"
  val ClusterName        = "flint:cluster_name"
  val ClusterDockerImage = "flint:cluster_docker_image"
  val ClusterTTL         = "flint:cluster_ttl"
  val ClusterIdleTimeout = "flint:cluster_idle_timeout"
  val ContainerState     = "flint:container_state"
  val DockerImage        = "flint:docker_image"
  val PlacementGroup     = "flint:placement_group"
  val SparkRole          = "flint:spark_cluster_role"
  val SubnetId           = "flint:subnet_id"
  val WorkerInstanceType = "flint:worker_instance_type"
  val WorkerBidPrice     = "flint:worker_bid_price"

  def apply(extraTags: ExtraTags): FlintTags = new FlintTags(extraTags)

  def commonResourceRequestTags(
      clusterId: ClusterId,
      clusterName: String,
      role: SparkClusterRole): Seq[(String, String)] =
    Map(
      ResourceName -> s"Flint Spark ${role.name} : $clusterName",
      ClusterId    -> clusterId.toString,
      ClusterName  -> clusterName,
      SparkRole    -> role.name).toIndexedSeq

  def dockerImageTags(dockerImage: DockerImage): Seq[(String, String)] =
    Seq(ClusterDockerImage -> dockerImage.canonicalName, DockerImage -> dockerImage.canonicalName)

  def isFlintTag(key: String): Boolean = key.startsWith("flint:") || key == ResourceName

  def validateUserTags(userTags: ExtraTags): Either[Iterable[String], Unit] =
    userTags.tags.keys.filter(isFlintTag) match {
      case x if x.isEmpty => Right(())
      case invalidKeys    => Left(invalidKeys.map("Invalid tag key: " + _))
    }
}
