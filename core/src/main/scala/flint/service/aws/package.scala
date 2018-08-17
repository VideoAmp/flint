package flint
package service

import Information._, InstanceState._

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
    InstanceSpecs(C5d9xlarge.toString, 36, GiB(64), InstanceStorageSpec(1, GiB(900)), "1.728"),
    InstanceSpecs(I38xlarge.toString, 32, GiB(236), InstanceStorageSpec(4, GiB(1900)), "2.496"),
    InstanceSpecs(I316xlarge.toString, 64, GiB(460), InstanceStorageSpec(8, GiB(1900)), "4.992"),
    InstanceSpecs(I3Metal.toString, 64, GiB(500), InstanceStorageSpec(8, GiB(1900)), "4.992"),
    InstanceSpecs(P32xlarge.toString, 8, GiB(53), "3.06"),
    InstanceSpecs(P38xlarge.toString, 32, GiB(236), "12.24"),
    InstanceSpecs(P316xlarge.toString, 64, GiB(480), "24.48"),
    InstanceSpecs(R5dLarge.toString, 2, GiB(14), InstanceStorageSpec(1, GiB(75)), "0.166"),
    InstanceSpecs(R5d12xlarge.toString, 48, GiB(276), InstanceStorageSpec(2, GiB(900)), "3.456"),
    InstanceSpecs(R5d24xlarge.toString, 96, GiB(760), InstanceStorageSpec(4, GiB(900)), "6.912"),
    InstanceSpecs(X116xlarge.toString, 64, GiB(968), InstanceStorageSpec(1, GiB(1920)), "6.669"),
    InstanceSpecs(X132xlarge.toString, 128, GiB(1944), InstanceStorageSpec(2, GiB(1920)), "13.338"),
    InstanceSpecs(X1e8xlarge.toString, 32, GiB(968), InstanceStorageSpec(1, GiB(960)), "6.672"),
    InstanceSpecs(X1e16xlarge.toString, 64, GiB(1944), InstanceStorageSpec(1, GiB(1920)), "13.344"),
    InstanceSpecs(X1e32xlarge.toString, 128, GiB(3896), InstanceStorageSpec(2, GiB(1920)), "26.688")
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
