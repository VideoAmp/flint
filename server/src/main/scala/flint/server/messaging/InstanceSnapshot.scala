package flint
package server
package messaging

private[messaging] case class InstanceSnapshot(
    id: String,
    ipAddress: Option[String],
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
      placementGroup,
      dockerImage.now,
      state.now,
      containerState.now,
      specs.instanceType)
  }
}
