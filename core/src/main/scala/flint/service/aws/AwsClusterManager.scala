package flint
package service
package aws

import java.net.InetAddress

import scala.collection.JavaConverters._
import scala.concurrent.Future

import com.amazonaws.auth._
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.services.ec2._
import com.amazonaws.services.ec2.model.{ Instance => _, _ }
import com.amazonaws.util.Base64
import com.typesafe.config.Config

import configs.syntax._

import rx._

class AwsClusterManager(flintConfig: Config)(implicit ctx: Ctx.Owner) extends ClusterManager {
  import AwsClusterManager._

  private val dockerConfig = flintConfig.get[Config]("docker").value
  private val awsConfig    = flintConfig.get[Config]("aws").value

  private lazy val ec2Client = createEc2Client(awsConfig)
  private lazy val instanceStatusPoller =
    new InstanceStatusPoller(awsConfig, ec2Client)

  def launchCluster(spec: ClusterSpec): Future[Cluster] = {
    val blockDeviceMappings = (("b", 0) :: ("c", 1) :: Nil).map {
      case (deviceLetter, virtualNumber) => createBlockDeviceMapping(deviceLetter, virtualNumber)
    }
    val masterUserData =
      createUserData(SparkClusterRole.master, spec.dockerImage, blockDeviceMappings, dockerConfig)
    val masterRequest =
      createRunInstancesRequest(
        spec.masterInstanceSpecs.awsInstanceType,
        numInstances = 1,
        blockDeviceMappings,
        masterUserData,
        awsConfig)

    ec2Client
      .runInstances(masterRequest)
      .map { reservation =>
        val masterAwsInstance = reservation.getInstances.asScala.head
        val instanceId        = masterAwsInstance.getInstanceId
        val ipAddress         = InetAddress.getByName(masterAwsInstance.getPrivateIpAddress)
        val lifecycleState    = instanceStatusPoller.getOrCreateInstanceLifecycleState(instanceId)
        val masterInstance =
          Instance(instanceId, ipAddress, lifecycleState, spec.masterInstanceSpecs)(() =>
            terminateInstances(instanceId))
        SparkMaster(masterInstance, 7077)
      }
      .flatMap { master =>
        val tags =
          Seq(
            ("cluster_id", spec.id.toString),
            ("Name", "Flint Spark Master : " + spec.owner),
            ("lifetime_hours", spec.ttl.map(_.toHours).getOrElse(1).toString),
            ("docker_image", spec.dockerImage.tag)).map {
            case (name, value) => new Tag(name, value)
          }
        val createTagsRequest =
          new CreateTagsRequest().withResources(master.instance.id).withTags(tags: _*)
        ec2Client.createTags(createTagsRequest).map(_ => master)
      }
      .flatMap { master =>
        val workerSpecs = spec.workerInstanceSpecs
        val workerUserData =
          createWorkerUserData(
            master.instance.ipAddress,
            workerSpecs,
            spec.dockerImage,
            blockDeviceMappings,
            dockerConfig)
        val workersRequest =
          createRunInstancesRequest(
            workerSpecs.awsInstanceType,
            spec.numWorkers,
            blockDeviceMappings,
            workerUserData,
            awsConfig)
        ec2Client.runInstances(workersRequest).map(r => (master, r))
      }
      .flatMap {
        case (master, reservation) =>
          val workers = reservation.getInstances.asScala.map { workerInstance =>
            val instanceId = workerInstance.getInstanceId
            val ipAddress  = InetAddress.getByName(workerInstance.getPrivateIpAddress)
            val lifecycleState =
              instanceStatusPoller.getOrCreateInstanceLifecycleState(instanceId)
            Instance(instanceId, ipAddress, lifecycleState, spec.workerInstanceSpecs)(() =>
              terminateInstances(instanceId))
          }.toIndexedSeq

          val tags =
            Seq(
              ("cluster_id", spec.id.toString),
              ("Name", "Flint Spark Worker : " + spec.owner),
              ("docker_image", spec.dockerImage.tag)).map {
              case (name, value) => new Tag(name, value)
            }
          val createTagsRequest =
            new CreateTagsRequest().withResources(workers.map(_.id): _*).withTags(tags: _*)
          ec2Client.createTags(createTagsRequest).map(_ => (master, workers))
      }
      .map {
        case (master, workers) =>
          val workersRx = Var(workers)
          Cluster(
            spec.id,
            Var(spec.dockerImage),
            spec.owner,
            spec.ttl,
            spec.idleTimeout,
            master,
            workersRx)(() => terminateClusterInstances(master, workersRx))
      }
  }

  private def terminateClusterInstances(
      master: SparkMaster,
      workers: Rx[Seq[Instance]]): Future[Unit] =
    terminateInstances((master.instance +: workers.now).map(_.id): _*)

  private def terminateInstances(instanceIds: String*): Future[Unit] = {
    val terminateInstancesRequest =
      new TerminateInstancesRequest().withInstanceIds(instanceIds: _*)
    ec2Client.terminateInstances(terminateInstancesRequest).map { terminatingInstances =>
      terminatingInstances.foreach { terminatingInstance =>
        instanceStatusPoller.updateInstanceLifecycleState(
          terminatingInstance.getInstanceId,
          terminatingInstance.getCurrentState)
      }
    }
  }
}

private[service] object AwsClusterManager {
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
      instanceType: InstanceType,
      numInstances: Int,
      blockDeviceMappings: Seq[BlockDeviceMapping],
      userData: String,
      awsConfig: Config) = {

    val amiId = awsConfig.get[String]("ami_id").value

    val iamInstanceProfile = new IamInstanceProfileSpecification().withArn(
      awsConfig
        .get[Config]("iam_instance_profile_specification")
        .flatMap(_.get[String]("arn"))
        .value)

    new RunInstancesRequest(amiId, numInstances, numInstances)
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
  }

  // private[service] for testing
  private[service] def createBlockDeviceMapping(
      deviceLetter: String,
      virtualNumber: Int): BlockDeviceMapping = {
    new BlockDeviceMapping()
      .withDeviceName("/dev/sd" + deviceLetter)
      .withVirtualName("ephemeral" + virtualNumber)
  }

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
         |mkdir ${ scratchVolumeMountPoints.mkString(" ") }
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

  // private[service] for testing
  private[service] def createWorkerUserData(
      masterIpAddress: InetAddress,
      workerSpecs: InstanceSpecs,
      dockerImage: DockerImage,
      blockDeviceMappings: Seq[BlockDeviceMapping],
      dockerConfig: Config): String = {
    val baseUserData =
      createUserData(SparkClusterRole.worker, dockerImage, blockDeviceMappings, dockerConfig)
    baseUserData
      .replaceMacro("WORKER_MEMORY", workerSpecs.memory + "g")
      .replaceMacro("SPARK_MASTER_IP", masterIpAddress.getHostAddress)
  }
}
