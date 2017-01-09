package flint

case class InstanceSpecs(
    instanceType: String,
    cores: Int,
    memory: Space,
    storage: InstanceStorageSpec,
    hourlyPrice: BigDecimal)

case class InstanceStorageSpec(devices: Int, storagePerDevice: Space) {
  def totalStorage: Space = storagePerDevice * devices
}

object InstanceSpecs {
  def apply(instanceType: String, cores: Int, memory: Space, hourlyPrice: String): InstanceSpecs =
    InstanceSpecs(instanceType, cores, memory, InstanceStorageSpec(0, GiB(0)), hourlyPrice)

  def apply(
      instanceType: String,
      cores: Int,
      memory: Space,
      storage: InstanceStorageSpec,
      hourlyPrice: String): InstanceSpecs =
    InstanceSpecs(instanceType.toString, cores, memory, storage, BigDecimal(hourlyPrice))
}
