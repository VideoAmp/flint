package flint
package service

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

import com.amazonaws.services.ec2.model.{
  InstanceState => AwsInstanceState,
  InstanceStateName => AwsInstanceStateName,
  InstanceType
}, InstanceType._

package object aws {
  private[aws] val instanceSpecs = Seq(
    InstanceSpecs(T2Micro.toString, 1, GiB(1), "0.012", isSpotEligible = false),
    InstanceSpecs(C38xlarge.toString, 32, GiB(52), InstanceStorageSpec(2, GiB(320)), "1.68"),
    InstanceSpecs(D28xlarge.toString, 36, GiB(236), InstanceStorageSpec(24, GiB(2000)), "5.52"),
    InstanceSpecs(I38xlarge.toString, 32, GiB(236), InstanceStorageSpec(4, GiB(1900)), "2.496"),
    InstanceSpecs(I316xlarge.toString, 64, GiB(460), InstanceStorageSpec(8, GiB(1900)), "4.992"),
    InstanceSpecs(M4Large.toString, 2, GiB(6), "0.1"),
    InstanceSpecs(P2Xlarge.toString, 4, GiB(52), "0.9"),
    InstanceSpecs(P28xlarge.toString, 32, GiB(460), "7.2"),
    InstanceSpecs(P216xlarge.toString, 64, GiB(716), "14.4"),
    InstanceSpecs(R3Large.toString, 2, GiB(13), InstanceStorageSpec(1, GiB(32)), "0.166"),
    InstanceSpecs(R38xlarge.toString, 32, GiB(236), InstanceStorageSpec(2, GiB(320)), "2.66"),
    InstanceSpecs(X116xlarge.toString, 64, GiB(960), InstanceStorageSpec(1, GiB(1920)), "6.669"),
    InstanceSpecs(X132xlarge.toString, 128, GiB(1940), InstanceStorageSpec(2, GiB(1920)), "13.338")
  )

  private[aws] val instanceSpecsMap =
    instanceSpecs.map(specs => specs.instanceType -> specs).toMap

  private[aws] implicit def awsInstanceState2FlintInstanceState(
      instanceState: AwsInstanceState): InstanceState = {
    val instanceStateName = AwsInstanceStateName.fromValue(instanceState.getName)

    instanceStateName match {
      case AwsInstanceStateName.Pending      => Starting
      case AwsInstanceStateName.Running      => Running
      case AwsInstanceStateName.ShuttingDown => Terminating
      case AwsInstanceStateName.Terminated   => Terminated
      case AwsInstanceStateName.Stopping     => Terminating
      case AwsInstanceStateName.Stopped =>
        sys.error(s"Unexpected instance state: $instanceStateName")
    }
  }

  private[aws] implicit class InstanceSpecsClusterSpec(clusterSpec: ClusterSpec) {
    def masterInstanceSpecs(): InstanceSpecs = instanceSpecsMap(clusterSpec.masterInstanceType)

    def workerInstanceSpecs(): InstanceSpecs = instanceSpecsMap(clusterSpec.workerInstanceType)
  }

  private[aws] implicit class InstanceTypeInstanceSpecs(instanceSpecs: InstanceSpecs) {
    def awsInstanceType(): InstanceType = InstanceType.fromValue(instanceSpecs.instanceType)
  }

  private[aws] val MAX_CLIENT_CONNECTIONS = Runtime.getRuntime.availableProcessors * 2

  /**
    * We create our own fixed-thread-pool executor service for AWS clients to share. We use daemon
    * threads in this executor service so that they don't block JVM exit
    */
  private[aws] lazy val awsExecutorService: ExecutorService =
    Executors.newFixedThreadPool(MAX_CLIENT_CONNECTIONS, flintThreadFactory("aws"))
}
