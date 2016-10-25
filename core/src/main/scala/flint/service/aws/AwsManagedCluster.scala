package flint
package service
package aws

import scala.concurrent.Future

import com.amazonaws.services.ec2.model.{ Instance => AwsInstance }
import com.typesafe.scalalogging.LazyLogging

import rx._

private[aws] class AwsManagedCluster(
    override val cluster: Cluster,
    clusterService: AwsClusterService,
    workerInstanceType: String)
    extends ManagedCluster
    with LazyLogging {
  override protected val managementService = clusterService.managementService

  override def terminate(): Future[Unit] =
    terminateClusterInstances(cluster.master, cluster.workers)

  override protected def addWorkers0(count: Int) =
    clusterService
      .addWorkers(
        cluster.master,
        None,
        cluster.id,
        cluster.dockerImage.now,
        cluster.owner,
        cluster.ttl,
        cluster.idleTimeout,
        count,
        workerInstanceType)
      .map(_ => ())

  override protected def changeDockerImage0(dockerImage: DockerImage): Future[Unit] =
    super.changeDockerImage0(dockerImage).flatMap { _ =>
      val dockerImageTags =
        Tags.dockerImageTags(dockerImage, clusterService.legacyCompatibility)
      clusterService.tagInstances(Seq(cluster.master), dockerImageTags)
    }

  private[aws] def update(instances: Seq[AwsInstance]): Unit =
    Tags.findMaster(cluster.id, instances).foreach { masterAwsInstance =>
      def updateInstance(instance: Instance, awsInstance: AwsInstance) {
        instance.dockerImage.asVar() = Tags.getDockerImage(awsInstance)
        instance.instanceState.asVar() = awsInstance.getState
        Tags.getContainerState(awsInstance).foreach(instance.containerState.asVar() = _)
      }

      Tags.getClusterDockerImage(masterAwsInstance).foreach(cluster.dockerImage.asVar() = _)

      updateInstance(cluster.master, masterAwsInstance)

      val workerInstances =
        Tags
          .filterWorkers(cluster.id, instances)
          .map(workerInstance => workerInstance.getInstanceId -> workerInstance)
          .toMap

      val workersNow = cluster.workers.now

      // Update `cluster.workers` from `workerInstances` in three steps:
      // 1. Retain workers present in `workerInstances`
      val retainedWorkers =
        workersNow.filter(worker => workerInstances.contains(worker.id))

      // 2. Update retained workers
      retainedWorkers.foreach { worker =>
        val awsWorker = workerInstances(worker.id)
        updateInstance(worker, awsWorker)
      }

      // 3. Create new workers not present in `cluster.workers`
      val newWorkers = workerInstances.filterNot {
        case (workerId, _) =>
          workersNow.map(_.id).contains(workerId)
      }.map { case (_, workerInstance) => clusterService.flintInstance(workerInstance) }

      cluster.workers.asVar() = retainedWorkers ++ newWorkers
    }

  private def terminateClusterInstances(
      master: Instance,
      workers: Rx[Seq[Instance]]): Future[Unit] =
    clusterService.terminateInstances((master +: workers.now).map(_.id): _*)
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
            val ttl         = Tags.getClusterTTL(masterAwsInstance)
            val idleTimeout = Tags.getClusterIdleTimeout(masterAwsInstance)
            val master      = clusterService.flintInstance(masterAwsInstance)
            val workers =
              Tags.filterWorkers(clusterId, instances).map(clusterService.flintInstance)

            val cluster =
              Cluster(clusterId, clusterDockerImage, owner, ttl, idleTimeout, master, workers)

            new AwsManagedCluster(cluster, clusterService, workerInstanceType)
          }
        }
      }
    }
}
