package flint

import java.time.Instant

case class SpotPrice(instanceType: String, price: BigDecimal, timestamp: Instant)
