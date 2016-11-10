package flint
package server
package messaging

import service.{ ClusterService, ManagedCluster }

import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging

import rx._

private[messaging] final class MessagingProtocol(
    clusterService: ClusterService,
    messageSender: MessageSender[ServerMessage],
    messageReceiver: MessageReceiver)(implicit ctx: Ctx.Owner)
    extends LazyLogging {
  import messageSender.sendMessage

  Rx {
    val clusterSystem = clusterService.clusterSystem
    clusterSystem.clusters.now.map(_._2).foreach(addClusterObservers)
    clusterSystem.newCluster.foreach(_.foreach(addClusterObservers))
  }

  private def addClusterObservers(cluster: ManagedCluster) = {
    cluster.cluster.instances.now.foreach(addInstanceObservers)
    cluster.newWorker.foreach(_.foreach(addInstanceObservers))
  }

  private def addInstanceObservers(instance: Instance) = {
    val instanceIdentityHashCode = "%010d".format(System.identityHashCode(instance))
    val instanceName             = instanceIdentityHashCode + ":" + instance.id

    instance.dockerImage.foreach { dockerImage =>
      sendMessage(InstanceDockerImage(instance.id, dockerImage))
      logger.trace(
        s"Instance docker image change. " +
          s"Instance $instanceName, " +
          s"docker image ${dockerImage}")
    }

    instance.instanceState.foreach { state =>
      sendMessage(InstanceState(instance.id, state))
      logger.trace(
        s"Instance state change. " +
          s"Instance $instanceName, " +
          s"state ${state}")
    }

    instance.containerState.foreach { state =>
      sendMessage(InstanceContainerState(instance.id, state))
      logger.trace(
        s"Container state change. " +
          s"Instance $instanceName, " +
          s"state ${state}")
    }
  }

  messageReceiver.receivedMessage.foreach { optMessage =>
    optMessage.collect { case clientMessage: ClientMessage => clientMessage }.foreach { message =>
      logger.debug(s"Received $message")
    }
  }

  private val response: Rx[Future[Option[ServerMessage]]] = Rx {
    messageReceiver.receivedMessage() match {
      case Some(AddWorkers(clusterId, count)) =>
        clusterService.clusterSystem.clusters.now
          .get(clusterId)
          .map(_.addWorkers(count))
          .map { optAddWorkers =>
            optAddWorkers.map(_ => None).recover {
              case ex =>
                val error = s"Failed to add workers to cluster with id: $clusterId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Add workers: no cluster with id: $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error => sendMessage(WorkerAdditionAttempt(clusterId, count, error)))
          .map(Some(_))
      case Some(ChangeDockerImage(clusterId, dockerImage)) =>
        clusterService.clusterSystem.clusters.now
          .get(clusterId)
          .map(_.changeDockerImage(dockerImage))
          .map { optChangeDockerImage =>
            optChangeDockerImage.map(_ => None).recover {
              case ex =>
                val error = s"Failed to change Docker image of cluster with id: $clusterId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Change docker image: no cluster with id: $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error => sendMessage(DockerImageChangeAttempt(clusterId, dockerImage, error)))
          .map(Some(_))
      case Some(LaunchCluster(clusterSpec)) =>
        clusterService
          .launchCluster(clusterSpec)
          .map(_ => None)
          .recover {
            case ex =>
              val error = s"Failed to launch cluster for spec: $clusterSpec"
              logger.error(error, ex)
              Some(error)
          }
          .flatMap(error => sendMessage(ClusterLaunchAttempt(clusterSpec, error)))
          .map(Some(_))
      case Some(TerminateCluster(clusterId)) =>
        clusterService.clusterSystem.clusters.now
          .get(clusterId)
          .map(_.terminate)
          .map { optTermination =>
            optTermination.map(_ => None).recover {
              case ex =>
                val error = s"Failed to terminate cluster with id: $clusterId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Terminate cluster: no cluster with id: $clusterId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(ClusterTerminationAttempt(clusterId, ClientRequested, error)))
          .map(Some(_))
      case Some(TerminateWorker(instanceId)) =>
        clusterService.clusterSystem.clusters.now.values
          .flatMap(_.cluster.unterminatedWorkers.now)
          .find(_.id == instanceId)
          .map(_.terminate)
          .map { optTermination =>
            optTermination.map(_ => None).recover {
              case ex =>
                val error = s"Failed to terminate worker with instance id: $instanceId"
                logger.error(error, ex)
                Some(error)
            }
          }
          .getOrElse {
            val error = s"Terminate worker: no worker with instance id: $instanceId"
            logger.error(error)
            Future.successful(Some(error))
          }
          .flatMap(error =>
            sendMessage(WorkerTerminationAttempt(instanceId, ClientRequested, error)))
          .map(Some(_))
      case Some(clientMessage: ClientMessage) =>
        logger.error(s"Don't know how to handle client message: $clientMessage")
        Future.successful(None)
      case Some(_: ServerMessage) | None => Future.successful(None)
    }
  }

  response.foreach { futureOptMessage =>
    futureOptMessage.foreach { optMessage =>
      optMessage.foreach { message =>
        logger.debug(s"Replied with $message")
      }
    }
  }
}
