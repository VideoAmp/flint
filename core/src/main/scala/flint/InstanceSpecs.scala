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
