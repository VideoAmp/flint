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
import com.amazonaws.services.ec2.model.{ Instance => AwsInstance, _ }
import com.amazonaws.util.Base64
import com.typesafe.config.Config

import configs.syntax._

import rx._

class AwsClusterService(flintConfig: Config)(implicit ctx: Ctx.Owner) extends ClusterService {
  import AwsClusterService._

  private val dockerConfig = flintConfig.get[Config]("docker").value
  private val awsConfig    = flintConfig.get[Config]("aws").value

  private lazy val ec2Client = createEc2Client(awsConfig)
  private lazy val awsClusters =
    new AwsClusters(this, awsConfig.get[Config]("clusters_refresh").value)

  override def clusters: Rx[Map[ClusterId, ManagedCluster]] = awsClusters.clusters

  override def launchCluster(spec: ClusterSpec): Future[ManagedCluster] = {
    val masterUserData =
      createUserData(SparkClusterRole.Master, spec.dockerImage, blockDeviceMappings, dockerConfig)
    val masterRequest =
      createRunInstancesRequest(
        Some(s"${spec.id}-master"),
        spec.masterInstanceSpecs.awsInstanceType,
        numInstances = 1,
        spec.placementGroup,
        blockDeviceMappings,
        masterUserData,
        awsConfig)

    ec2Client
      .runInstances(masterRequest)
      .map { reservation =>
        val masterAwsInstance = reservation.getInstances.asScala.head
        flintInstance(masterAwsInstance)
      }
      .flatMap { master =>
        val tags = Tags.instanceTags(
          spec,
          SparkClusterRole.Master,
          includeLegacyTags = awsConfig.get[Boolean]("legacy_compatibility").value)
        val createTagsRequest =
          new CreateTagsRequest().withResources(master.id).withTags(tags: _*)
        ec2Client.createTags(createTagsRequest).map(_ => master)
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
          spec.workerInstanceType,
          spec.placementGroup).map(r => (master, r))
      }
      .flatMap {
        case (master, workers) =>
          val tags = Tags.instanceTags(
            spec,
            SparkClusterRole.Worker,
            includeLegacyTags = awsConfig.get[Boolean]("legacy_compatibility").value)
          val createTagsRequest =
            new CreateTagsRequest().withResources(workers.map(_.id): _*).withTags(tags: _*)
          ec2Client.createTags(createTagsRequest).map(_ => (master, workers))
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
          awsClusters.clusters() = awsClusters.clusters.now.updated(spec.id, managedCluster)
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
      instanceType: String,
      placementGroup: Option[String]): Future[Seq[Instance]] = {
    val workerSpecs = instanceSpecsMap(instanceType)
    val workerUserData =
      createWorkerUserData(
        master.ipAddress,
        workerSpecs,
        dockerImage,
        blockDeviceMappings,
        dockerConfig)
    val workersRequest =
      createRunInstancesRequest(
        clientToken,
        workerSpecs.awsInstanceType,
        numWorkers,
        placementGroup,
        blockDeviceMappings,
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
        includeLegacyTags = awsConfig.get[Boolean]("legacy_compatibility").value)
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
    val instanceSpecs =
      instanceSpecsMap(awsInstance.getInstanceType)

    val placementGroup = Option(awsInstance.getPlacement.getGroupName).filterNot(_.isEmpty)

    Instance(instanceId, ipAddress, placementGroup, lifecycleState, instanceSpecs)(() =>
      terminateInstances(instanceId))
  }

  private[aws] def terminateInstances(instanceIds: String*): Future[Unit] = {
    val terminateInstancesRequest =
      new TerminateInstancesRequest().withInstanceIds(instanceIds: _*)
    ec2Client.terminateInstances(terminateInstancesRequest).map { terminatingInstances =>
      terminatingInstances.foreach { terminatingInstance =>
        awsClusters.updateInstanceLifecycleState(
          terminatingInstance.getInstanceId,
          terminatingInstance.getCurrentState)
      }
    }
  }
}

private[aws] object AwsClusterService {
  private val blockDeviceMappings = (("b", 0) :: ("c", 1) :: Nil).map {
    case (deviceLetter, virtualNumber) => createBlockDeviceMapping(deviceLetter, virtualNumber)
  }

  private def createEc2Client(awsConfig: Config): Ec2Client = {
    val accessKey           = awsConfig.get[String]("access_key").value
    val secretAccessKey     = awsConfig.get[String]("secret_access_key").value
    val credentials         = new BasicAWSCredentials(accessKey, secretAccessKey)
    val credentialsProvider = new AWSStaticCredentialsProvider(credentials)

    val awsEc2Client = AmazonEC2AsyncClientBuilder.standard
      .withRegion(awsConfig.get[String]("region").value)
      .withCredentials(credentialsProvider)
      .withExecutorFactory(new ExecutorFactory {
        override def newExecutor() = flintExecutionContext
      })
      .build

    new Ec2Client(awsEc2Client)
  }

  private def createRunInstancesRequest(
      clientToken: Option[String],
      instanceType: InstanceType,
      numInstances: Int,
      placementGroup: Option[String],
      blockDeviceMappings: Seq[BlockDeviceMapping],
      userData: String,
      awsConfig: Config) = {
    val amiId = awsConfig.get[String]("ami_id").value

    val iamInstanceProfile = new IamInstanceProfileSpecification().withArn(
      awsConfig
        .get[Config]("iam_instance_profile_specification")
        .flatMap(_.get[String]("arn"))
        .value)

    val request = new RunInstancesRequest(amiId, numInstances, numInstances)
      .withBlockDeviceMappings(blockDeviceMappings: _*)
      .withDisableApiTermination(false)
      .withEbsOptimized(false)
      .withIamInstanceProfile(iamInstanceProfile)
      .withInstanceInitiatedShutdownBehavior(ShutdownBehavior.Terminate)
      .withInstanceType(instanceType)
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

  // private[service] for testing
  private[service] def createBlockDeviceMapping(
      deviceLetter: String,
      virtualNumber: Int): BlockDeviceMapping =
    new BlockDeviceMapping()
      .withDeviceName("/dev/sd" + deviceLetter)
      .withVirtualName("ephemeral" + virtualNumber)

  // private[service] for testing
  private[service] def createUserData(
      clusterRole: SparkClusterRole,
      dockerImage: DockerImage,
      blockDeviceMappings: Seq[BlockDeviceMapping],
      dockerConfig: Config
  ): String = {
    val scratchVolumeMountPoints = (1 to blockDeviceMappings.length).map("/scratch" + _)

    val chunks = Seq.newBuilder[String]
    chunks +=
      s"""#!/bin/bash
         |
         |mkdir ${scratchVolumeMountPoints.mkString(" ")}
         |""".stripMargin

    chunks ++= blockDeviceMappings.zip(scratchVolumeMountPoints).flatMap {
      case (mapping, mountPoint) =>
        val internalDeviceName =
          mapping.getDeviceName.replaceFirst("/dev/sd", "/dev/xvd")

        (s"mkfs.ext4 $internalDeviceName" :: s"mount $internalDeviceName $mountPoint" :: "" :: Nil)
    }

    val baseTemplate =
      readTextResource("user_data.sh.template")
        .replaceMacro("AUTH_CREDZ_BASE64", dockerConfig.getString("auth"))
        .replaceMacro("EMAIL", dockerConfig.getString("email"))

    chunks += baseTemplate
    chunks += ""

    val instanceTemplate =
      readTextResource(s"user_data-$clusterRole.sh.template")
        .replaceMacro("SPARK_LOCAL_DIRS", scratchVolumeMountPoints.mkString(","))
        .replaceMacro(
          "SCRATCH_VOLUMES",
          scratchVolumeMountPoints.map(x => s"-v $x:$x").mkString(" "))
        .replaceMacro("IMAGE_TAG", dockerImage.tag)

    chunks += instanceTemplate

    chunks.result.mkString("\n")
  }

  private def createWorkerUserData(
      masterIpAddress: InetAddress,
      workerSpecs: InstanceSpecs,
      dockerImage: DockerImage,
      blockDeviceMappings: Seq[BlockDeviceMapping],
      dockerConfig: Config): String = {
    val baseUserData =
      createUserData(SparkClusterRole.Worker, dockerImage, blockDeviceMappings, dockerConfig)
    baseUserData
      .replaceMacro("WORKER_MEMORY", workerSpecs.memory + "g")
      .replaceMacro("SPARK_MASTER_IP", masterIpAddress.getHostAddress)
  }
}
