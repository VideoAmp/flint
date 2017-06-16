package flint
package service
package aws

import java.net.InetAddress

import scala.concurrent.Future

import com.amazonaws.services.ec2.model.{ Instance => AwsInstance }
import com.typesafe.scalalogging.LazyLogging

import rx._

private[aws] class AwsManagedCluster(
    override val cluster: Cluster,
    clusterService: AwsClusterService,
    override val workerInstanceType: String,
    override val workerBidPrice: Option[BigDecimal])
    extends ManagedCluster
    with LazyLogging {
  override protected val managementService = clusterService.managementService

  override protected def terminate0(): Future[Unit] =
    clusterService.terminateCluster(cluster, isSpot = workerBidPrice.isDefined)

  override protected def addWorkers0(count: Int) =
    clusterService
      .launchWorkers(
        cluster.master,
        None,
        cluster.id,
        cluster.dockerImage.now,
        cluster.owner,
        count,
        workerInstanceType,
        workerBidPrice)
      .map { newWorkers =>
        logger.debug(s"Adding ${newWorkers.size} new worker(s)")
        this.newWorkers.asVar() = newWorkers
        cluster.workers.asVar() = cluster.workers.now ++ newWorkers
        newWorkers
      }

  override protected def changeDockerImage0(dockerImage: DockerImage): Future[Unit] =
    super.changeDockerImage0(dockerImage).flatMap { _ =>
      val dockerImageTags = Tags.dockerImageTags(dockerImage)
      clusterService.tagResources(Seq(cluster.master.id), dockerImageTags)
    }

  private[aws] def update(instances: Seq[AwsInstance]): Unit =
    Tags.findMaster(cluster.id, instances).foreach { masterAwsInstance =>
      def updateInstance(instance: Instance, awsInstance: AwsInstance) = {
        instance.ipAddress.asVar() =
          Option(awsInstance.getPrivateIpAddress).map(InetAddress.getByName)
        instance.dockerImage.asVar() = Tags.getDockerImage(awsInstance)
        instance.state.asVar() = awsInstance.getState
        Tags.getContainerState(awsInstance).foreach(instance.containerState.asVar() = _)
      }

      Tags.getClusterDockerImage(masterAwsInstance).foreach(cluster.dockerImage.asVar() = _)

      updateInstance(cluster.master, masterAwsInstance)

      val workerAwsInstances =
        Tags
          .filterWorkers(cluster.id, instances)
          .map(workerInstance => workerInstance.getInstanceId -> workerInstance)
          .toMap

      val workersNow = cluster.workers.now

      // Update `cluster.workers` from `workerInstances` in three steps:
      // 1. Retain workers present in `workerInstances`
      val (retainedWorkers, removedWorkers) =
        workersNow.partition(worker => workerAwsInstances.contains(worker.id))

      // 2. Update retained workers
      retainedWorkers.foreach { worker =>
        val awsWorker = workerAwsInstances(worker.id)
        updateInstance(worker, awsWorker)
      }

      // 3. Create new workers not present in `cluster.workers`
      val newWorkers = workerAwsInstances.filterNot {
        case (workerId, _) =>
          workersNow.map(_.id).contains(workerId)
      }.map {
        case (_, workerInstance) =>
          clusterService.flintInstance(cluster.id, workerInstance, workerBidPrice.isDefined)
      }

      if (newWorkers.nonEmpty) {
        logger.debug(s"Adding ${newWorkers.size} new worker(s)")
        this.newWorkers.asVar() = newWorkers.toIndexedSeq
      }

      if (removedWorkers.nonEmpty) {
        logger.debug(s"Removing ${removedWorkers.size} retired worker(s)")
        this.removedWorkers.asVar() = removedWorkers.map(_.id).toIndexedSeq
      }

      cluster.workers.asVar() = retainedWorkers ++ newWorkers
    }
}

private[aws] object AwsManagedCluster {
  def forInstances(
      clusterId: ClusterId,
      instances: Seq[AwsInstance],
      clusterService: AwsClusterService)(implicit ctx: Ctx.Owner): Option[AwsManagedCluster] =
    Tags.findMaster(clusterId, instances).flatMap { masterAwsInstance =>
      Tags.getClusterDockerImage(masterAwsInstance).flatMap { clusterDockerImage =>
        Tags.getOwner(masterAwsInstance).flatMap { owner =>
          Tags.getWorkerInstanceType(masterAwsInstance).map { workerInstanceType =>
            val ttl            = Tags.getClusterTTL(masterAwsInstance)
            val idleTimeout    = Tags.getClusterIdleTimeout(masterAwsInstance)
            val workerBidPrice = Tags.getWorkerBidPrice(masterAwsInstance)
            val master =
              clusterService.flintInstance(clusterId, masterAwsInstance, false)
            val workers =
              Tags
                .filterWorkers(clusterId, instances)
                .map(instance =>
                  clusterService.flintInstance(clusterId, instance, workerBidPrice.isDefined))

            val cluster =
              Cluster(
                clusterId,
                clusterDockerImage,
                owner,
                ttl,
                idleTimeout,
                master,
                workers,
                masterAwsInstance.getLaunchTime.toInstant)

            new AwsManagedCluster(cluster, clusterService, workerInstanceType, workerBidPrice)
          }
        }
      }
    }
}
