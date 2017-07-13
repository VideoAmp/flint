package flint

case class InstanceSpecs(
    instanceType: String,
    cores: Int,
    memory: Space,
    storage: InstanceStorageSpec,
    hourlyPrice: BigDecimal,
    isSpotEligible: Boolean)

case class InstanceStorageSpec(devices: Int, storagePerDevice: Space) {
  def totalStorage: Space = storagePerDevice * devices
}

object InstanceSpecs {
  def apply(instanceType: String, cores: Int, memory: Space, hourlyPrice: String): InstanceSpecs =
    InstanceSpecs(
      instanceType,
      cores,
      memory,
      InstanceStorageSpec(0, GiB(0)),
      BigDecimal(hourlyPrice),
      true)

  def apply(
      instanceType: String,
      cores: Int,
      memory: Space,
      hourlyPrice: String,
      isSpotEligible: Boolean): InstanceSpecs =
    InstanceSpecs(
      instanceType,
      cores,
      memory,
      InstanceStorageSpec(0, GiB(0)),
      BigDecimal(hourlyPrice),
      isSpotEligible)

  def apply(
      instanceType: String,
      cores: Int,
      memory: Space,
      storage: InstanceStorageSpec,
      hourlyPrice: String): InstanceSpecs =
    InstanceSpecs(instanceType, cores, memory, storage, BigDecimal(hourlyPrice), true)
}
