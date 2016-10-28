package flint
package service
package aws

import java.net.InetAddress
import java.time.Duration

import scala.collection.JavaConverters._
import scala.concurrent.Future

import com.amazonaws.auth._
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.services.ec2._
import com.amazonaws.services.ec2.model.{ Instance => AwsInstance, Storage => _, _ }
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsyncClientBuilder
import com.amazonaws.util.Base64
import com.typesafe.config.Config

import configs.syntax._

import rx._

class AwsClusterService(flintConfig: Config)(implicit ctx: Ctx.Owner) extends ClusterService {
  import AwsClusterService._

  private val dockerConfig = flintConfig.get[Config]("docker").value
  private val awsConfig    = flintConfig.get[Config]("aws").value

  private[aws] val legacyCompatibility = awsConfig.get[Boolean]("legacy_compatibility").value

  private lazy val ssmClient                        = createSsmClient(awsConfig)
  override val managementService: ManagementService = new AwsManagementService(ssmClient)

  private lazy val ec2Client = createEc2Client(awsConfig)

  override lazy val clusterSystem =
    new AwsClusterSystem(this, awsConfig.get[Config]("clusters_refresh").value)

  override def launchCluster(spec: ClusterSpec): Future[ManagedCluster] = {
    val blockDeviceMappings = createBlockDeviceMappings(spec.masterInstanceSpecs.storage)
    val masterUserData =
      createUserData(
        SparkClusterRole.Master,
        spec.dockerImage,
        blockDeviceMappings,
        awsConfig,
        dockerConfig)
    val masterRequest =
      createRunInstancesRequest(
        Some(s"${spec.id}-master"),
        spec.masterInstanceSpecs,
        numInstances = 1,
        spec.placementGroup,
        masterUserData,
        awsConfig)

    ec2Client
      .runInstances(masterRequest)
      .map { reservation =>
        val masterAwsInstance = reservation.getInstances.asScala.head
        flintInstance(masterAwsInstance)
      }
      .flatMap { master =>
        val tags =
          Tags.instanceTags(spec, SparkClusterRole.Master, legacyCompatibility)
        tagInstances(Seq(master.id), tags).map(_ => master)
      }
      .flatMap { master =>
        addWorkers(
          master,
          Some(s"${spec.id}-initial_workers"),
          spec.id,
          spec.dockerImage,
          spec.owner,
          spec.ttl,
          spec.idleTimeout,
          spec.numWorkers,
          spec.workerInstanceType).map(r => (master, r))
      }
      .flatMap {
        case (master, workers) =>
          val tags = Tags.instanceTags(spec, SparkClusterRole.Worker, legacyCompatibility)
          tagInstances(workers.map(_.id), tags).map(_ => (master, workers))
      }
      .map {
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
                workers),
              this,
              spec.workerInstanceType)
          clusterSystem.addCluster(managedCluster)
          managedCluster
      }
  }

  private[aws] def addWorkers(
      master: Instance,
      clientToken: Option[String],
      clusterId: ClusterId,
      dockerImage: DockerImage,
      owner: String,
      ttl: Option[Duration],
      idleTimeout: Option[Duration],
      numWorkers: Int,
      instanceType: String): Future[Seq[Instance]] = {
    val workerSpecs = instanceSpecsMap(instanceType)
    val workerUserData =
      createWorkerUserData(master.ipAddress, workerSpecs, dockerImage, awsConfig, dockerConfig)
    val workersRequest =
      createRunInstancesRequest(
        clientToken,
        workerSpecs,
        numWorkers,
        master.placementGroup,
        workerUserData,
        awsConfig)
    ec2Client.runInstances(workersRequest).flatMap { reservation =>
      val workers = reservation.getInstances.asScala.map(flintInstance).toIndexedSeq
      val tags = Tags.instanceTags(
        clusterId,
        dockerImage,
        owner,
        ttl,
        idleTimeout,
        instanceType,
        SparkClusterRole.Worker,
        legacyCompatibility)
      val createTagsRequest =
        new CreateTagsRequest().withResources(workers.map(_.id): _*).withTags(tags: _*)
      ec2Client.createTags(createTagsRequest).map(_ => workers)
    }
  }

  private[aws] def describeFlintInstances(): Future[Seq[Reservation]] = {
    val flintInstanceFilter = new Filter("tag-key").withValues(Tags.ClusterId)
    val request             = new DescribeInstancesRequest().withFilters(flintInstanceFilter)
    ec2Client.describeInstances(request)
  }

  private[aws] def flintInstance(awsInstance: AwsInstance): Instance = {
    val instanceId                     = awsInstance.getInstanceId
    val ipAddress                      = InetAddress.getByName(awsInstance.getPrivateIpAddress)
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
      instanceSpecs)(() => terminateInstances(instanceId))
  }

  private[aws] def tagInstances(instanceIds: Seq[String], tags: Seq[Tag]): Future[Unit] = {
    val createTagsRequest =
      new CreateTagsRequest().withResources(instanceIds: _*).withTags(tags: _*)
    ec2Client.createTags(createTagsRequest)
  }

  private[aws] def terminateInstances(instanceIds: String*): Future[Unit] = {
    val terminateInstancesRequest =
      new TerminateInstancesRequest().withInstanceIds(instanceIds: _*)
    ec2Client.terminateInstances(terminateInstancesRequest).map { terminatingInstances =>
      terminatingInstances.foreach { terminatingInstance =>
        tagInstances(
          instanceIds.toStream,
          Seq(new Tag(Tags.ContainerState, ContainerStopped.toString)))
        clusterSystem.updateInstanceState(
          terminatingInstance.getInstanceId,
          terminatingInstance.getCurrentState)
      }
    }
  }
}

private[aws] object AwsClusterService {
  private def createAwsEc2Client(awsConfig: Config): AmazonEC2Async = {
    val accessKey           = awsConfig.get[String]("access_key").value
    val secretAccessKey     = awsConfig.get[String]("secret_access_key").value
    val credentials         = new BasicAWSCredentials(accessKey, secretAccessKey)
    val credentialsProvider = new AWSStaticCredentialsProvider(credentials)

    AmazonEC2AsyncClientBuilder.standard
      .withRegion(awsConfig.get[String]("region").value)
      .withCredentials(credentialsProvider)
      .withExecutorFactory(new ExecutorFactory {
        override def newExecutor() = flintExecutionContext
      })
      .build
  }

  private def createEc2Client(awsConfig: Config): Ec2Client = {
    val awsEc2Client = createAwsEc2Client(awsConfig)
    new Ec2Client(awsEc2Client)
  }

  private def createSsmClient(awsConfig: Config): SsmClient = {
    val accessKey           = awsConfig.get[String]("access_key").value
    val secretAccessKey     = awsConfig.get[String]("secret_access_key").value
    val credentials         = new BasicAWSCredentials(accessKey, secretAccessKey)
    val credentialsProvider = new AWSStaticCredentialsProvider(credentials)

    val awsSsmClient = AWSSimpleSystemsManagementAsyncClientBuilder.standard
      .withRegion(awsConfig.get[String]("region").value)
      .withCredentials(credentialsProvider)
      .withExecutorFactory(new ExecutorFactory {
        override def newExecutor() = flintExecutionContext
      })
      .build

    new SsmClient(awsSsmClient)
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

  private def createBlockDeviceMapping(
      deviceLetter: String,
      virtualNumber: Int): BlockDeviceMapping =
    new BlockDeviceMapping()
      .withDeviceName("/dev/sd" + deviceLetter)
      .withVirtualName("ephemeral" + virtualNumber)

  // private[aws] for testing
  private[aws] def createBlockDeviceMappings(storage: Storage) =
    (0 until storage.devices).map { virtualNumber =>
      val deviceLetter = ('b' + virtualNumber).toChar.toString
      createBlockDeviceMapping(deviceLetter, virtualNumber)
    }

  // private[aws] for testing
  private[aws] def createUserData(
      clusterRole: SparkClusterRole,
      dockerImage: DockerImage,
      blockDeviceMappings: Seq[BlockDeviceMapping],
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

    def replaceContainerTagMacros(text: String): String =
      text
        .replaceMacro("CONTAINER_STATE_TAG_KEY", Tags.ContainerState)
        .replaceMacro("CONTAINER_PENDING_STATE_TAG_VALUE", ContainerPending)
        .replaceMacro("CONTAINER_RUNNING_STATE_TAG_VALUE", ContainerRunning)
        .replaceMacro("CONTAINER_STARTING_STATE_TAG_VALUE", ContainerStarting)
        .replaceMacro("CONTAINER_STOPPED_STATE_TAG_VALUE", ContainerStopped)
        .replaceMacro("CONTAINER_STOPPING_STATE_TAG_VALUE", ContainerStopping)
        .replaceMacro("DOCKER_IMAGE_TAG_KEY", Tags.DockerImage)
        .replaceMacro("AWS_REGION", awsConfig.getString("region"))
        .replaceMacro("DOCKER_AUTH", dockerConfig.getString("auth"))
        .replaceMacro("DOCKER_EMAIL", dockerConfig.getString("email"))
        .replaceMacro("SPARK_LOCAL_DIRS", sparkLocalDirs.mkString(","))
        .replaceMacro("SCRATCH_VOLUMES", sparkLocalDirs.map(x => s"-v $x:$x").mkString(" "))
        .replaceMacro("IMAGE_TAG", dockerImage.tag)

    val baseTemplate = readTextResource("user_data.sh.template")

    chunks += replaceContainerTagMacros(baseTemplate)
    chunks += ""

    val instanceTemplate = readTextResource(
      s"user_data-${clusterRole.name.toLowerCase}.sh.template")

    chunks += replaceContainerTagMacros(instanceTemplate)

    chunks.result.mkString("\n")
  }

  private def createWorkerUserData(
      masterIpAddress: InetAddress,
      workerSpecs: InstanceSpecs,
      dockerImage: DockerImage,
      awsConfig: Config,
      dockerConfig: Config): String = {
    val blockDeviceMappings = createBlockDeviceMappings(workerSpecs.storage)
    val baseUserData =
      createUserData(
        SparkClusterRole.Worker,
        dockerImage,
        blockDeviceMappings,
        awsConfig,
        dockerConfig)
    baseUserData
      .replaceMacro("WORKER_MEMORY", workerSpecs.memory + "g")
      .replaceMacro("SPARK_MASTER_IP", masterIpAddress.getHostAddress)
  }
}
