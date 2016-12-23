package flint
package service

import com.amazonaws.services.ec2.model.{ InstanceState, InstanceStateName, InstanceType },
InstanceType._

package object aws {
  private[aws] val instanceSpecs = Seq(
    InstanceSpecs(T2Micro.toString, 1, GiB(1), "0.013"),
    InstanceSpecs(C38xlarge.toString, 32, GiB(52), InstanceStorageSpec(2, GiB(320)), "1.68"),
    InstanceSpecs(M4Large.toString, 2, GiB(6), "0.12"),
    InstanceSpecs(P28xlarge.toString, 32, GiB(460), "7.2"),
    InstanceSpecs(P216xlarge.toString, 64, GiB(716), "14.4"),
    InstanceSpecs(R3Large.toString, 2, GiB(13), InstanceStorageSpec(1, GiB(32)), "0.166"),
    InstanceSpecs(R38xlarge.toString, 32, GiB(236), InstanceStorageSpec(2, GiB(320)), "2.66"),
    InstanceSpecs(X116xlarge.toString, 64, GiB(960), InstanceStorageSpec(1, GiB(1920)), "6.669"),
    InstanceSpecs(
      X132xlarge.toString,
      128,
      GiB(1940),
      InstanceStorageSpec(2, GiB(1920)),
      "13.338"))

  private[aws] val instanceSpecsMap =
    instanceSpecs.map(specs => specs.instanceType -> specs).toMap

  private[aws] implicit def instanceState2LifecycleState(
      instanceState: InstanceState): LifecycleState = {
    val instanceStateName = InstanceStateName.fromValue(instanceState.getName)

    instanceStateName match {
      case InstanceStateName.Pending      => Starting
      case InstanceStateName.Running      => Running
      case InstanceStateName.ShuttingDown => Terminating
      case InstanceStateName.Terminated   => Terminated
      case InstanceStateName.Stopping     => Terminating
      case InstanceStateName.Stopped =>
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
}
