package flint

import java.time.Instant

case class SpotPrice(
    instanceType: String,
    availabilityZone: String,
    price: BigDecimal,
    timestamp: Instant)
