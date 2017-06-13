package flint
package service
package aws

import java.net.InetAddress
import java.time.Instant
import java.time.temporal.ChronoUnit.HOURS
import java.util.Date
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Success

import com.amazonaws.{ ClientConfiguration, PredefinedClientConfigurations }
import com.amazonaws.auth._
import com.amazonaws.client.builder.{ AwsAsyncClientBuilder, ExecutorFactory }
import com.amazonaws.services.ec2.{ AmazonEC2Async, AmazonEC2AsyncClientBuilder }
import com.amazonaws.services.ec2.model.{
  Instance => AwsInstance,
  SpotPrice => _,
  Storage => _,
  _
}
import com.amazonaws.services.simplesystemsmanagement.{
  AWSSimpleSystemsManagementAsync,
  AWSSimpleSystemsManagementAsyncClientBuilder
}
import com.amazonaws.util.Base64
import com.typesafe.config.Config

import configs.syntax._

import rx._

class AwsClusterService(flintConfig: Config)(implicit ctx: Ctx.Owner) extends ClusterService {
  import AwsClusterService._

  private val dockerConfig = flintConfig.get[Config]("docker").value
  private val awsConfig    = flintConfig.get[Config]("aws").value

  private val extraInstanceTags = awsConfig.get[Map[String, String]]("extra_instance_tags").value
  private val tags              = new Tags(extraInstanceTags)

  private lazy val ssmClient                        = createSsmClient(awsConfig)
  override val managementService: ManagementService = new AwsManagementService(ssmClient)

  private lazy val ec2Client = createEc2Client(awsConfig)

  override val clusterSystem =
    new AwsClusterSystem(this, awsConfig.get[Config]("clusters_refresh").value)

  private val clusterReaper = new ClusterReaper(clusterSystem.runningClusters)
  updateExecutorService.scheduleWithFixedDelay(clusterReaper, 0, 1, TimeUnit.MINUTES)

  override val instanceSpecs = aws.instanceSpecs

  override def getSpotPrices(instanceTypes: String*): Future[Seq[SpotPrice]] =
    if (instanceTypes.isEmpty) { Future.successful(Seq.empty) } else {
      val oneHourAgo = Instant.now.minus(1, HOURS)
      val describeSpotPriceHistoryRequest =
        new DescribeSpotPriceHistoryRequest()
          .withInstanceTypes(instanceTypes: _*)
          .withProductDescriptions(RIProductDescription.LinuxUNIX.toString)
          .withAvailabilityZone(awsConfig.get[String]("availability_zone").value)
          .withStartTime(new Date(oneHourAgo.toEpochMilli))
      ec2Client
        .describeSpotPriceHistory(describeSpotPriceHistoryRequest)
        .map(
          _.map(
            awsSpotPrice =>
              SpotPrice(
                awsSpotPrice.getInstanceType,
                BigDecimal(awsSpotPrice.getSpotPrice),
                awsSpotPrice.getTimestamp.toInstant)))
    }

  override def launchCluster(spec: ClusterSpec): Future[ManagedCluster] =
    launchCluster(spec, workerBidPrice = None)

  override def launchSpotCluster(
      spec: ClusterSpec,
      workerBidPrice: BigDecimal): Future[ManagedCluster] =
    launchCluster(spec, Some(workerBidPrice))

  private def launchCluster(
      spec: ClusterSpec,
      workerBidPrice: Option[BigDecimal]): Future[ManagedCluster] =
    launchMaster(spec, workerBidPrice).flatMap { master =>
      launchWorkers(
        master,
        Some(s"${spec.id}-initial_workers"),
        spec.id,
        spec.dockerImage,
        spec.owner,
        spec.ttl,
        spec.idleTimeout,
        spec.numWorkers,
        spec.workerInstanceType,
        workerBidPrice).map(workers => (master, workers))
    }.map {
      case (master, workers) =>
        val managedCluster =
          new AwsManagedCluster(
            Cluster(
              spec.id,
              spec.dockerImage,
              spec.owner,
              spec.ttl,
              spec.idleTimeout,
              master,
              workers,
              Instant.now),
            this,
            spec.workerInstanceType,
            workerBidPrice)
        clusterSystem.addCluster(managedCluster)
        managedCluster
    }

  private def launchMaster(
      spec: ClusterSpec,
      workerBidPrice: Option[BigDecimal]): Future[Instance] = {
    val blockDeviceMappings = createBlockDeviceMappings(spec.masterInstanceSpecs.storage)
    val masterUserData =
      createUserData(
        spec.id,
        spec.owner,
        SparkClusterRole.Master,
        InstanceProvisioning.Normal,
        spec.dockerImage,
        blockDeviceMappings,
        extraInstanceTags,
        awsConfig,
        dockerConfig)
    val masterRequest =
      createRunInstancesRequest(
        Some(s"${spec.id}-master"),
        spec.masterInstanceSpecs,
        numInstances = 1,
        // Don't put the master in the cluster placement group. For one thing, AWS only allows a
        // limited variety of instance types to be placed in a placement group. Attempting to place
        // an unsupported instance type in a placement group will fail. For another, the master's
        // network bandwidth requirement is effectively zilch
        placementGroup = None,
        masterUserData,
        awsConfig)

    ec2Client
      .runInstances(masterRequest)
      .map { reservation =>
        val masterAwsInstance = reservation.getInstances.asScala.head
        flintInstance(spec.id, masterAwsInstance, false)
      }
      .flatMap { master =>
        val masterTags = tags.masterTags(spec, workerBidPrice)
        tagResources(Seq(master.id), masterTags).map(_ => master)
      }
  }

  private[aws] def launchWorkers(
      master: Instance,
      clientToken: Option[String],
      clusterId: ClusterId,
      dockerImage: DockerImage,
      owner: String,
      ttl: Option[FiniteDuration],
      idleTimeout: Option[FiniteDuration],
      numWorkers: Int,
      instanceType: String,
      workerBidPrice: Option[BigDecimal]): Future[Seq[Instance]] =
    if (numWorkers > 0) {
      workerBidPrice
        .map(
          launchSpotProvisionedWorkers(
            master,
            clientToken,
            clusterId,
            dockerImage,
            owner,
            ttl,
            idleTimeout,
            numWorkers,
            instanceType,
            _))
        .getOrElse(
          launchNormallyProvisionedWorkers(
            master,
            clientToken,
            clusterId,
            dockerImage,
            owner,
            ttl,
            idleTimeout,
            numWorkers,
            instanceType))
    } else {
      Future.successful(Seq.empty)
    }

  private def launchNormallyProvisionedWorkers(
      master: Instance,
      clientToken: Option[String],
      clusterId: ClusterId,
      dockerImage: DockerImage,
      owner: String,
      ttl: Option[FiniteDuration],
      idleTimeout: Option[FiniteDuration],
      numWorkers: Int,
      instanceType: String): Future[Seq[Instance]] = {
    val workerSpecs = instanceSpecsMap(instanceType)
    val workerUserData =
      createWorkerUserData(
        clusterId,
        owner,
        InstanceProvisioning.Normal,
        master.ipAddress.now.get, // Unsafe but whatevs
        workerSpecs,
        dockerImage,
        extraInstanceTags,
        awsConfig,
        dockerConfig)
    val workersRequest =
      createRunInstancesRequest(
        clientToken,
        workerSpecs,
        numWorkers,
        master.placementGroup,
        workerUserData,
        awsConfig)
    ec2Client.runInstances(workersRequest).flatMap { reservation =>
      val workers =
        reservation.getInstances.asScala
          .map(instance => flintInstance(clusterId, instance, false))
          .toIndexedSeq
      val workerTags = tags.workerTags(clusterId, owner)
      tagResources(workers.map(_.id), workerTags).map(_ => workers)
    }
  }

  private def launchSpotProvisionedWorkers(
      master: Instance,
      clientToken: Option[String],
      clusterId: ClusterId,
      dockerImage: DockerImage,
      owner: String,
      ttl: Option[FiniteDuration],
      idleTimeout: Option[FiniteDuration],
      numWorkers: Int,
      instanceType: String,
      bidPrice: BigDecimal): Future[Seq[Instance]] = {
    val workerSpecs = instanceSpecsMap(instanceType)
    val workerUserData =
      createWorkerUserData(
        clusterId,
        owner,
        InstanceProvisioning.Spot,
        master.ipAddress.now.get, // Unsafe but whatevs
        workerSpecs,
        dockerImage,
        extraInstanceTags,
        awsConfig,
        dockerConfig)
    val workersRequest =
      createRequestSpotInstancesRequest(
        clientToken,
        workerSpecs,
        numWorkers,
        master.placementGroup,
        workerUserData,
        bidPrice,
        awsConfig)
    ec2Client.requestSpotInstances(workersRequest).flatMap { spotInstancesRequests =>
      val spotInstanceRequestIds  = spotInstancesRequests.map(_.getSpotInstanceRequestId)
      val spotInstanceRequestTags = Tags.spotInstanceRequestTags(clusterId, owner)
      tagResources(spotInstanceRequestIds, spotInstanceRequestTags).map(_ => Seq.empty)
    }
  }

  private[aws] def describeFlintInstances(): Future[Seq[Reservation]] = {
    val clusterIdTagFilter = new Filter("tag-key").withValues(Tags.ClusterId)
    val request            = new DescribeInstancesRequest().withFilters(clusterIdTagFilter)
    ec2Client.describeInstances(request)
  }

  private[aws] def flintInstance(
      clusterId: ClusterId,
      awsInstance: AwsInstance,
      isSpot: Boolean): Instance = {
    val instanceId = awsInstance.getInstanceId
    val ipAddress =
      Option(awsInstance.getPrivateIpAddress).map(InetAddress.getByName)
    val lifecycleState: LifecycleState = awsInstance.getState
    val containerState =
      Tags.getContainerState(awsInstance).getOrElse(ContainerPending)
    val instanceSpecs =
      instanceSpecsMap(awsInstance.getInstanceType)
    val dockerImage    = Tags.getDockerImage(awsInstance)
    val placementGroup = Option(awsInstance.getPlacement.getGroupName).filterNot(_.isEmpty)

    Instance(
      instanceId,
      ipAddress,
      placementGroup,
      dockerImage,
      lifecycleState,
      containerState,
      instanceSpecs)(instance => terminateInstances(clusterId, Seq(instance), isSpot))
  }

  private[aws] def tagResources(resourceIds: Seq[String], tags: Seq[Tag]): Future[Unit] =
    if (resourceIds.nonEmpty) {
      val createTagsRequest =
        new CreateTagsRequest().withResources(resourceIds: _*).withTags(tags: _*)
      ec2Client.createTags(createTagsRequest)
    } else {
      Future.successful(())
    }

  private[aws] def terminateCluster(cluster: Cluster, isSpot: Boolean): Future[Unit] = {
    val spotRequestCancellation = if (isSpot) {
      cancelSpotRequests(cluster.id, None)
    } else { Future.successful(()) }
    spotRequestCancellation.flatMap(_ =>
      terminateInstances(cluster.id, (cluster.master +: cluster.workers.now), false))
  }

  private def cancelSpotRequests(
      clusterId: ClusterId,
      instancesFilterOpt: Option[Seq[Instance]]): Future[Unit] = {
    val clusterIdFilter =
      new Filter(s"tag:${Tags.ClusterId}").withValues(clusterId.toString)
    val describeSpotInstanceRequestsRequest =
      new DescribeSpotInstanceRequestsRequest().withFilters(clusterIdFilter)
    ec2Client
      .describeSpotInstanceRequests(describeSpotInstanceRequestsRequest)
      .map { spotInstanceRequests =>
        instancesFilterOpt.map { instances =>
          val instanceIds = instances.map(_.id)
          spotInstanceRequests.filter(spotInstanceRequest =>
            instanceIds.contains(spotInstanceRequest.getInstanceId))
        }.getOrElse(spotInstanceRequests)
      }
      .flatMap { spotInstanceRequests =>
        val spotInstanceRequestIds = spotInstanceRequests.map(_.getSpotInstanceRequestId)
        if (spotInstanceRequestIds.nonEmpty) {
          val cancelSpotInstanceRequestsRequest = new CancelSpotInstanceRequestsRequest()
            .withSpotInstanceRequestIds(spotInstanceRequestIds: _*)
          ec2Client.cancelSpotInstanceRequests(cancelSpotInstanceRequestsRequest)
        } else {
          Future.successful(())
        }
      }
  }

  private def terminateInstances(
      clusterId: ClusterId,
      instances: Seq[Instance],
      cancelSpotRequests: Boolean): Future[Unit] =
    if (instances.nonEmpty) {
      val instanceIds = instances.map(_.id)
      val spotRequestCancellation = if (cancelSpotRequests) {
        this.cancelSpotRequests(clusterId, Some(instances))
      } else { Future.successful(()) }
      spotRequestCancellation.flatMap { _ =>
        val terminateInstancesRequest =
          new TerminateInstancesRequest().withInstanceIds(instanceIds: _*)
        ec2Client
          .terminateInstances(terminateInstancesRequest)
          .andThen {
            case Success(_) => instances.foreach(_.containerState.asVar() = ContainerStopped)
          }
          .map { terminatingInstances =>
            terminatingInstances.foreach { terminatingInstance =>
              tagResources(
                instanceIds.toStream,
                Seq(new Tag(Tags.ContainerState, ContainerStopped.toString))).foreach { _ =>
                clusterSystem.updateInstanceState(
                  terminatingInstance.getInstanceId,
                  terminatingInstance.getCurrentState)
              }
            }
          }
      }
    } else {
      Future.successful(())
    }
}

private[aws] object AwsClusterService {
  private def createEc2Client(awsConfig: Config): Ec2Client = {
    val clientBuilder = AmazonEC2AsyncClientBuilder.standard

    new Ec2Client(
      createAwsClient[AmazonEC2Async, AmazonEC2AsyncClientBuilder](awsConfig, clientBuilder))
  }

  private def createSsmClient(awsConfig: Config): SsmClient = {
    val clientBuilder = AWSSimpleSystemsManagementAsyncClientBuilder.standard

    // scalastyle:off
    new SsmClient(
      createAwsClient[
        AWSSimpleSystemsManagementAsync,
        AWSSimpleSystemsManagementAsyncClientBuilder](awsConfig, clientBuilder))
    // scalastyle:on
  }

  private def createAwsClient[T, B <: AwsAsyncClientBuilder[B, T]](
      awsConfig: Config,
      clientBuilder: B): T = {
    val accessKey           = awsConfig.get[String]("access_key").value
    val secretAccessKey     = awsConfig.get[String]("secret_access_key").value
    val credentials         = new BasicAWSCredentials(accessKey, secretAccessKey)
    val credentialsProvider = new AWSStaticCredentialsProvider(credentials)

    val clientConfiguration =
      new ClientConfiguration(PredefinedClientConfigurations.defaultConfig)
        .withMaxConnections(MAX_CLIENT_CONNECTIONS)

    clientBuilder
      .withClientConfiguration(clientConfiguration)
      .withRegion(awsConfig.get[String]("region").value)
      .withCredentials(credentialsProvider)
      .withExecutorFactory(new ExecutorFactory {
        override def newExecutor() = awsExecutorService
      })
      .build
  }

  private def createRunInstancesRequest(
      clientToken: Option[String],
      instanceSpecs: InstanceSpecs,
      numInstances: Int,
      placementGroup: Option[String],
      userData: String,
      awsConfig: Config) = {
    val amiId = awsConfig.get[String]("ami_id").value

    val iamInstanceProfile = new IamInstanceProfileSpecification().withArn(
      awsConfig
        .get[Config]("iam_instance_profile_specification")
        .flatMap(_.get[String]("arn"))
        .value)

    val blockDeviceMappings = createBlockDeviceMappings(instanceSpecs.storage)

    val request = new RunInstancesRequest(amiId, numInstances, numInstances)
      .withBlockDeviceMappings(blockDeviceMappings: _*)
      .withDisableApiTermination(false)
      .withEbsOptimized(false)
      .withIamInstanceProfile(iamInstanceProfile)
      .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
      .withInstanceType(instanceSpecs.instanceType)
      .withKeyName(awsConfig.get[String]("key_name").value)
      .withMonitoring(false)
      .withSecurityGroupIds(awsConfig.get[Seq[String]]("security_groups").value: _*)
      .withSubnetId(awsConfig.get[String]("subnet_id").value)
      .withUserData(Base64.encodeAsString(userData.getBytes("UTF-8"): _*))

    val withClientToken = clientToken.map { clientToken =>
      request.withClientToken(clientToken)
    } getOrElse request

    placementGroup.map { placementGroup =>
      val placement = new Placement().withGroupName(placementGroup)
      withClientToken.withPlacement(placement)
    } getOrElse withClientToken
  }

  private def createRequestSpotInstancesRequest(
      clientToken: Option[String],
      instanceSpecs: InstanceSpecs,
      numInstances: Int,
      placementGroup: Option[String],
      userData: String,
      bidPrice: BigDecimal,
      awsConfig: Config) = {
    val amiId = awsConfig.get[String]("ami_id").value

    val iamInstanceProfile = new IamInstanceProfileSpecification().withArn(
      awsConfig
        .get[Config]("iam_instance_profile_specification")
        .flatMap(_.get[String]("arn"))
        .value)

    val blockDeviceMappings = createBlockDeviceMappings(instanceSpecs.storage)

    val securityGroups =
      awsConfig.get[Seq[String]]("security_groups").value.map(new GroupIdentifier().withGroupId)

    val launchSpec = new LaunchSpecification()
      .withAllSecurityGroups(securityGroups: _*)
      .withBlockDeviceMappings(blockDeviceMappings: _*)
      .withEbsOptimized(false)
      .withIamInstanceProfile(iamInstanceProfile)
      .withImageId(amiId)
      .withInstanceType(instanceSpecs.instanceType)
      .withKeyName(awsConfig.get[String]("key_name").value)
      .withSubnetId(awsConfig.get[String]("subnet_id").value)
      .withUserData(Base64.encodeAsString(userData.getBytes("UTF-8"): _*))

    val withPlacement = placementGroup.map { placementGroup =>
      val placement = new SpotPlacement().withGroupName(placementGroup)
      launchSpec.withPlacement(placement)
    } getOrElse launchSpec

    val request = new RequestSpotInstancesRequest(bidPrice.toString)
      .withInstanceCount(numInstances)
      .withLaunchSpecification(withPlacement)
      .withType(SpotInstanceType.Persistent)

    clientToken.map { clientToken =>
      request.withClientToken(clientToken)
    } getOrElse request
  }

  private def createBlockDeviceMapping(
      deviceLetter: String,
      virtualNumber: Int): BlockDeviceMapping =
    new BlockDeviceMapping()
      .withDeviceName("/dev/sd" + deviceLetter)
      .withVirtualName("ephemeral" + virtualNumber)

  // private[aws] for testing
  private[aws] def createBlockDeviceMappings(storage: InstanceStorageSpec) =
    (0 until storage.devices).map { virtualNumber =>
      val deviceLetter = ('b' + virtualNumber).toChar.toString
      createBlockDeviceMapping(deviceLetter, virtualNumber)
    }

  // private[aws] for testing
  private[aws] def createUserData(
      clusterId: ClusterId,
      owner: String,
      clusterRole: SparkClusterRole,
      provisioning: InstanceProvisioning,
      dockerImage: DockerImage,
      blockDeviceMappings: Seq[BlockDeviceMapping],
      extraInstanceTags: Map[String, String],
      awsConfig: Config,
      dockerConfig: Config
  ): String = {
    val scratchVolumeMountPoints = (1 to blockDeviceMappings.length).map("/scratch" + _)

    val chunks = Seq.newBuilder[String]
    chunks += "#!/bin/bash\n"

    if (scratchVolumeMountPoints.nonEmpty) {
      chunks += s"mkdir ${scratchVolumeMountPoints.mkString(" ")}\n"
    }

    chunks ++= blockDeviceMappings.zip(scratchVolumeMountPoints).flatMap {
      case (mapping, mountPoint) =>
        val internalDeviceName =
          mapping.getDeviceName.replaceFirst("/dev/sd", "/dev/xvd")

        (s"mkfs.ext4 $internalDeviceName" :: s"mount $internalDeviceName $mountPoint" :: "" :: Nil)
    }

    val sparkLocalDirs = if (scratchVolumeMountPoints.nonEmpty) {
      scratchVolumeMountPoints
    } else {
      Seq("/tmp")
    }

    val instanceName = {
      val provisioningSubstring = if (provisioning == InstanceProvisioning.Spot) {
        "Spot "
      } else {
        ""
      }
      s"Flint Spark $provisioningSubstring${clusterRole.name} : $owner"
    }

    def replaceContainerTagMacros(text: String): String =
      text
        .replaceMacro("AWS_REGION", awsConfig.getString("region"))
        .replaceMacro("CONTAINER_STATE_TAG_KEY", Tags.ContainerState)
        .replaceMacro("CONTAINER_PENDING_STATE_TAG_VALUE", ContainerPending)
        .replaceMacro("CONTAINER_RUNNING_STATE_TAG_VALUE", ContainerRunning)
        .replaceMacro("CONTAINER_STARTING_STATE_TAG_VALUE", ContainerStarting)
        .replaceMacro("CONTAINER_STOPPED_STATE_TAG_VALUE", ContainerStopped)
        .replaceMacro("CONTAINER_STOPPING_STATE_TAG_VALUE", ContainerStopping)
        .replaceMacro("DOCKER_AUTH", dockerConfig.getString("auth"))
        .replaceMacro("DOCKER_EMAIL", dockerConfig.getString("email"))
        .replaceMacro("SCRATCH_VOLUMES", sparkLocalDirs.map(x => s"-v $x:$x").mkString(" "))
        .replaceMacro("SPARK_LOCAL_DIRS", sparkLocalDirs.mkString(","))
        .replaceMacro("CLUSTER_ID_TAG_KEY", Tags.ClusterId)
        .replaceMacro("CLUSTER_ID_TAG_VALUE", clusterId)
        .replaceMacro("DOCKER_IMAGE_KEY", Tags.DockerImage)
        .replaceMacro("DOCKER_IMAGE_VALUE", dockerImage.canonicalName)
        .replaceMacro("NAME_TAG_VALUE", instanceName)
        .replaceMacro("OWNER_TAG_KEY", Tags.Owner)
        .replaceMacro("OWNER_TAG_VALUE", owner)
        .replaceMacro("SPARK_ROLE_TAG_KEY", Tags.SparkRole)
        .replaceMacro("SPARK_ROLE_TAG_VALUE", clusterRole.name)

    val baseTemplate = readTextResource("user_data-common.sh.template")

    val extraInstanceTagsString =
      extraInstanceTags.map {
        case (name, value) =>
          s"""Key="$name",Value="$value""""
      }.mkString(" ")

    chunks += replaceContainerTagMacros(baseTemplate)
      .replaceMacro("EXTRA_INSTANCE_TAGS", extraInstanceTagsString)
    chunks += ""

    val instanceTemplate = readTextResource(
      s"user_data-${clusterRole.name.toLowerCase}.sh.template")

    chunks += replaceContainerTagMacros(instanceTemplate)

    chunks.result.mkString("\n")
  }

  private def createWorkerUserData(
      clusterId: ClusterId,
      owner: String,
      provisioning: InstanceProvisioning,
      masterIpAddress: InetAddress,
      workerSpecs: InstanceSpecs,
      dockerImage: DockerImage,
      extraInstanceTags: Map[String, String],
      awsConfig: Config,
      dockerConfig: Config): String = {
    val blockDeviceMappings = createBlockDeviceMappings(workerSpecs.storage)
    val baseUserData =
      createUserData(
        clusterId,
        owner,
        SparkClusterRole.Worker,
        provisioning,
        dockerImage,
        blockDeviceMappings,
        extraInstanceTags,
        awsConfig,
        dockerConfig)
    baseUserData
      .replaceMacro("WORKER_MEMORY", workerSpecs.memory)
      .replaceMacro("SPARK_MASTER_IP", masterIpAddress.getHostAddress)
  }
}
