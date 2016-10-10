package flint
package service
package aws

import scala.concurrent.Future

import com.amazonaws.services.ec2.model.{ Instance => AwsInstance }

import rx._

private[aws] class AwsManagedCluster(
    override val cluster: Cluster,
    awsClusterService: AwsClusterService,
    workerInstanceType: String)
    extends ManagedCluster {
  override def terminate(): Future[Unit] =
    terminateClusterInstances(cluster.master, cluster.workers)

  override protected def addWorkers0(count: Int) =
    awsClusterService
      .addWorkers(
        cluster.master,
        None,
        cluster.id,
        cluster.dockerImage.now,
        cluster.owner,
        cluster.ttl,
        cluster.idleTimeout,
        count,
        workerInstanceType,
        cluster.master.placementGroup)
      .map(_ => ())

  override protected def changeDockerImage0(dockerImage: DockerImage) = ???

  private[aws] def update(instances: Seq[AwsInstance]): Unit =
    Tags.findMaster(cluster.id, instances).foreach { masterAwsInstance =>
      Tags.getDockerImage(masterAwsInstance).foreach(cluster.dockerImage.asVar() = _)
      cluster.master.lifecycleState.asVar() = masterAwsInstance.getState
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
        worker.lifecycleState.asVar() = workerInstances(worker.id).getState
      }

      // 3. Create new workers not present in `cluster.workers`
      val newWorkers = workerInstances.filterNot {
        case (workerId, _) =>
          workersNow.map(_.id).contains(workerId)
      }.map { case (_, workerInstance) => awsClusterService.flintInstance(workerInstance) }

      cluster.workers.asVar() = retainedWorkers ++ newWorkers
    }

  private def terminateClusterInstances(
      master: Instance,
      workers: Rx[Seq[Instance]]): Future[Unit] =
    awsClusterService.terminateInstances((master +: workers.now).map(_.id): _*)
}

private[aws] object AwsManagedCluster {
  def forInstances(
      clusterId: ClusterId,
      instances: Seq[AwsInstance],
      awsClusterService: AwsClusterService)(implicit ctx: Ctx.Owner): Option[AwsManagedCluster] =
    Tags.findMaster(clusterId, instances).flatMap { masterAwsInstance =>
      Tags.getDockerImage(masterAwsInstance).flatMap { dockerImage =>
        Tags.getOwner(masterAwsInstance).flatMap { owner =>
          Tags.getWorkerInstanceType(masterAwsInstance).map { workerInstanceType =>
            val ttl         = Tags.getClusterTTL(masterAwsInstance)
            val idleTimeout = Tags.getClusterIdleTimeout(masterAwsInstance)
            val master      = awsClusterService.flintInstance(masterAwsInstance)
            val workers =
              Tags.filterWorkers(clusterId, instances).map(awsClusterService.flintInstance)

            val cluster =
              Cluster(clusterId, dockerImage, owner, ttl, idleTimeout, master, workers)

            new AwsManagedCluster(cluster, awsClusterService, workerInstanceType)
          }
        }
      }
    }
}
