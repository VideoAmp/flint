package flint

import java.time.Instant

import scala.math.BigDecimal

case class SpotPrice(instanceType: String, price: BigDecimal, timestamp: Instant)
