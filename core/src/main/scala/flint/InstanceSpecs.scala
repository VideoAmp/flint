package flint

case class InstanceSpecs(
    instanceType: String,
    cores: Int,
    memory: Int,
    storage: Storage,
    hourlyPrice: BigDecimal)

case class Storage(devices: Int, storagePerDevice: Int)
