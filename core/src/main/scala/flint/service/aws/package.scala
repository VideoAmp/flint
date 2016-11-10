package flint
package service

import com.amazonaws.services.ec2.model.{ InstanceState, InstanceStateName, InstanceType },
InstanceType._

package object aws {
  private val instanceSpecSeq = Seq(
    instanceSpecs(T2Micro, 1, GiB(1), "0.013"),
    instanceSpecs(C38xlarge, 32, GiB(52), InstanceStorageSpec(2, GiB(320)), "1.68"),
    instanceSpecs(M4Large, 2, GiB(6), "0.12"),
    instanceSpecs(P28xlarge, 32, GiB(460), "7.2"),
    instanceSpecs(P216xlarge, 64, GiB(716), "14.4"),
    instanceSpecs(R3Large, 2, GiB(13), InstanceStorageSpec(1, GiB(32)), "0.166"),
    instanceSpecs(R38xlarge, 32, GiB(236), InstanceStorageSpec(2, GiB(320)), "2.66"),
    instanceSpecs(X116xlarge, 128, GiB(1940), InstanceStorageSpec(1, GiB(1920)), "13.338"),
    instanceSpecs(X132xlarge, 128, GiB(1940), InstanceStorageSpec(2, GiB(1920)), "13.338"))

  private def instanceSpecs(
      instanceType: InstanceType,
      cores: Int,
      memory: Space,
      hourlyPrice: String): InstanceSpecs =
    instanceSpecs(instanceType, cores, memory, InstanceStorageSpec(0, GiB(0)), hourlyPrice)

  private def instanceSpecs(
      instanceType: InstanceType,
      cores: Int,
      memory: Space,
      storage: InstanceStorageSpec,
      hourlyPrice: String): InstanceSpecs =
    InstanceSpecs(instanceType.toString, cores, memory, storage, BigDecimal(hourlyPrice))

  private[aws] val instanceSpecsMap =
    instanceSpecSeq.map(specs => specs.instanceType -> specs).toMap

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
