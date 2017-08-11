package flint

import Information._

case class InstanceSpecs(
    instanceType: String,
    cores: Int,
    memory: Information,
    storage: InstanceStorageSpec,
    hourlyPrice: BigDecimal,
    isSpotEligible: Boolean)

case class InstanceStorageSpec(devices: Int, storagePerDevice: Information) {
  def totalStorage: Information = storagePerDevice * devices
}

object InstanceSpecs {
  def apply(
      instanceType: String,
      cores: Int,
      memory: Information,
      hourlyPrice: String): InstanceSpecs =
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
      memory: Information,
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
      memory: Information,
      storage: InstanceStorageSpec,
      hourlyPrice: String): InstanceSpecs =
    InstanceSpecs(instanceType, cores, memory, storage, BigDecimal(hourlyPrice), true)
}
