package flint

import org.scalatest.{ FlatSpec, Matchers }

class SpaceSpec extends FlatSpec with Matchers {
  behavior of "fromBytes"

  import Space.Implicits._
  import Space.fromBytes

  it should "convert 0 to Bytes(0)" in {
    fromBytes(0) should be(0 Bytes)
  }

  it should "convert multiple of 1024 to KiB" in {
    val oneKiB = 1L << 10
    fromBytes(oneKiB) should be(1 KiB)
    fromBytes(5 * oneKiB) should be(5 KiB)
    fromBytes(16572 * oneKiB) should be(16572 KiB)
    fromBytes(-5 * oneKiB) should be(-5 KiB)
  }

  it should "convert multiple of 1024^2 to MiB" in {
    val oneMiB = 1L << 20
    fromBytes(oneMiB) should be(1 MiB)
    fromBytes(5 * oneMiB) should be(5 MiB)
    fromBytes(-5 * oneMiB) should be(-5 MiB)
  }

  it should "convert multiple of 1024^3 to GiB" in {
    val oneGiB = 1L << 30
    fromBytes(oneGiB) should be(1 GiB)
    fromBytes(5 * oneGiB) should be(5 GiB)
    fromBytes(-5 * oneGiB) should be(-5 GiB)
  }

  it should "convert multiple of 1024^4 to TiB" in {
    val oneTiB = 1L << 40
    fromBytes(oneTiB) should be(1 TiB)
    fromBytes(5 * oneTiB) should be(5 TiB)
    fromBytes(-5 * oneTiB) should be(-5 TiB)
  }

  it should "convert multiple of 1024^5 to PiB" in {
    val onePiB = 1L << 50
    fromBytes(onePiB) should be(1 PiB)
    fromBytes(5 * onePiB) should be(5 PiB)
    fromBytes(-5 * onePiB) should be(-5 PiB)
  }
}
