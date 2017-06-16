package flint
package server
package messaging

import service.{ ClusterService, ManagedCluster }

import scala.concurrent.Future
import scala.util.{ Failure, Success }

import com.typesafe.scalalogging.LazyLogging

import rx._

private[messaging] final class MessagingProtocol(
    serverId: String,
    clusterService: ClusterService,
    messageSender: MessageSender[ServerMessage],
    messageReceiver: MessageReceiver)(implicit ctx: Ctx.Owner)
    extends LazyLogging {
  // Even though messageNo will only be accessed by a single thread (from the update executor)
  // concurrently, that thread might change. Therefore, we mark it @volatile
  @volatile
  private var messageNo: Int = 0

  private def nextMessageNo(): Int = {
    messageNo += 1
    messageNo
  }

  Rx {
    val clusterSystem = clusterService.clusterSystem
    clusterSystem.clusters.now.map(_._2).foreach(addClusterObservers)
    clusterSystem.newClusters.foreach { managedClusters =>
      if (managedClusters.nonEmpty) {
        sendMessage(ClustersAdded(_, _, managedClusters.map(ClusterSnapshot(_)).toList)).foreach {
          _ =>
            managedClusters.foreach(addClusterObservers)
        }
      }
    }
    clusterSystem.removedClusters.foreach { clusterIds =>
      if (clusterIds.nonEmpty) {
        sendMessage(ClustersRemoved(_, _, clusterIds.toList))
      }
    }
  }

  private def sendMessage(messageFun: (String, Int) => ServerMessage): Future[ServerMessage] = {
    val message = messageFun(serverId, nextMessageNo)
    logger.trace(s"Sending message $message")
    messageSender.sendMessage(message).andThen {
      case Success(message) =>
        logger.trace(s"Sent message $message")
      case Failure(ex) =>
        logger.error(s"Failed to send message $message", ex)
    }
  }

  private def addClusterObservers(cluster: ManagedCluster) = {
    cluster.cluster.instances.now.foreach(addInstanceObservers)
    cluster.newWorkers.foreach { workers =>
      if (workers.nonEmpty) {
        sendMessage(WorkersAdded(
          _,
          _,
          cluster.cluster.id,
          workers.map(InstanceSnapshot(_)).toList)).foreach { _ =>
          workers.foreach(addInstanceObservers)
        }
      }
    }
    cluster.removedWorkers.foreach { workerIds =>
      if (workerIds.nonEmpty) {
        sendMessage(WorkersRemoved(_, _, cluster.cluster.id, workerIds.toList))
      }
    }
  }

  private def addInstanceObservers(instance: Instance) = {
    val instanceIdentityHashCode = "%010d".format(System.identityHashCode(instance))
    val instanceName             = instanceIdentityHashCode + ":" + instance.id

    instance.dockerImage.foreach { dockerImage =>
      sendMessage(InstanceDockerImage(_, _, instance.id, dockerImage))
      logger.debug(
        s"Instance docker image change. " +
          s"Instance $instanceName, " +
          s"docker image ${dockerImage}")
    }

    instance.ipAddress.foreach { ipAddress =>
      sendMessage(InstanceIpAddress(_, _, instance.id, ipAddress))
      logger.debug(
        s"Instance IP address change. " +
          s"Instance $instanceName, " +
          s"ip address ${ipAddress.orNull}")
    }

    instance.state.foreach { state =>
      sendMessage(InstanceState(_, _, instance.id, state))
      logger.debug(
        s"Instance state change. " +
          s"Instance $instanceName, " +
          s"state ${state}")
    }

    instance.effectiveContainerState.foreach { state =>
      sendMessage(InstanceContainerState(_, _, instance.id, state))
      logger.debug(
        s"Container state change. " +
          s"Instance $instanceName, " +
          s"state ${state}")
    }
  }

  private val response: Rx[Future[Option[ServerMessage]]] = Rx {
    val optMessage = messageReceiver.receivedMessage()
    optMessage.collect { case clientMessage: ClientMessage => clientMessage }.foreach { message =>
      logger.trace(s"Received $message")
    }
    optMessage match {
      case Some(AddWorkers(clusterId, count)) =>
        clusterService.clusterSystem.clusters.now
          .get(clusterId)
          .map(_.addWorkers(count))
          .map { optAddWorkers =>
            logger.debug(optAddWorkers.toString)
            optAddWorkers.map(_ => None).recover {
              case ex =>
                val error = s"Failed to add workers to cluster with id $clusterId: " + ex.getMessage
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Add workers: no cluster with id $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error => sendMessage(WorkerAdditionAttempt(_, _, clusterId, count, error)))
          .map(Some(_))
      case Some(ChangeDockerImage(clusterId, dockerImage)) =>
        clusterService.clusterSystem.clusters.now
          .get(clusterId)
          .map { cluster =>
            sendMessage(DockerImageChangeRequest(_, _, clusterId, dockerImage)).flatMap(_ =>
              cluster.changeDockerImage(dockerImage))
          }
          .map { optChangeDockerImage =>
            optChangeDockerImage.map(_ => None).recover {
              case ex =>
                val error =
                  s"Failed to change Docker image of cluster with id $clusterId: " + ex.getMessage
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Change docker image: no cluster with id $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(DockerImageChangeAttempt(_, _, clusterId, dockerImage, error)))
          .map(Some(_))
      case Some(LaunchCluster(clusterSpec)) =>
        clusterService
          .launchCluster(clusterSpec)
          .map(_ => None)
          .recover {
            case ex =>
              val error = s"Failed to launch cluster for spec $clusterSpec: " + ex.getMessage
              logger.error(error, ex)
              Some(error)
          }
          .flatMap(error => sendMessage(ClusterLaunchAttempt(_, _, clusterSpec, error)))
          .map(Some(_))
      case Some(LaunchSpotCluster(clusterSpec, bidPrice)) =>
        clusterService
          .launchSpotCluster(clusterSpec, bidPrice)
          .map(_ => None)
          .recover {
            case ex =>
              val error =
                s"Failed to launch spot cluster with bid price $bidPrice for spec $clusterSpec: " +
                  ex.getMessage
              logger.error(error, ex)
              Some(error)
          }
          .flatMap(error => sendMessage(ClusterLaunchAttempt(_, _, clusterSpec, error)))
          .map(Some(_))
      case Some(TerminateCluster(clusterId)) =>
        clusterService.clusterSystem.clusters.now
          .get(clusterId)
          .map(_.terminate)
          .map { optTermination =>
            optTermination.map(_ => None).recover {
              case ex =>
                val error = s"Failed to terminate cluster with id $clusterId: " + ex.getMessage
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Terminate cluster: no cluster with id $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(ClusterTerminationAttempt(_, _, clusterId, ClientRequested, error)))
          .map(Some(_))
      case Some(TerminateWorker(instanceId)) =>
        clusterService.clusterSystem.clusters.now.values
          .flatMap(_.cluster.unterminatedWorkers.now)
          .find(_.id == instanceId)
          .map(_.terminate)
          .map { optTermination =>
            optTermination.map(_ => None).recover {
              case ex =>
                val error =
                  s"Failed to terminate worker with instance id $instanceId: " + ex.getMessage
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Terminate worker: no worker with instance id $instanceId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(WorkerTerminationAttempt(_, _, instanceId, ClientRequested, error)))
          .map(Some(_))
      case Some(clientMessage: ClientMessage) =>
        logger.error(s"Don't know how to handle client message $clientMessage")
        Future.successful(None)
      case Some(_: ServerMessage) | None => Future.successful(None)
    }
  }
}
