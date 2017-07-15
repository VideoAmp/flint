package flint
package server
package messaging

private[messaging] case class InstanceSnapshot(
    id: String,
    ipAddress: Option[String],
    subnet: Option[Subnet],
    placementGroup: Option[String],
    dockerImage: Option[DockerImage],
    state: LifecycleState,
    containerState: ContainerState,
    instanceType: String)

private[messaging] object InstanceSnapshot {
  def apply(instance: Instance): InstanceSnapshot = {
    import instance._

    InstanceSnapshot(
      id,
      ipAddress.now.map(_.getHostAddress),
      subnet.now,
      placementGroup,
      dockerImage.now,
      state.now,
      effectiveContainerState.now,
      specs.instanceType)
  }
}
