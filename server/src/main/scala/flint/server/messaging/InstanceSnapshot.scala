package flint
package server
package messaging

import java.time.Instant

private[messaging] case class InstanceSnapshot(
    id: String,
    ipAddress: Option[String],
    subnet: Option[Subnet],
    placementGroup: Option[String],
    dockerImage: Option[DockerImage],
    state: flint.InstanceState,
    containerState: ContainerState,
    instanceType: String,
    launchedAt: Instant,
    terminatedAt: Option[Instant])

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
      specs.instanceType,
      launchedAt,
      terminatedAt.now
    )
  }
}
