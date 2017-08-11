package flint

sealed trait Information {
  import Information.fromBytes

  def bytes: BigInt

  def *(multiple: Int): Information = fromBytes(bytes * multiple)

  def *(multiple: Long): Information = fromBytes(bytes * multiple)
}

object Information {
  object Implicits {
    implicit class IntInformation(value: Int) {
      def Bytes: Bytes = new Bytes(value)

      def KiB: KiB = new KiB(value)

      def MiB: MiB = new MiB(value)

      def GiB: GiB = new GiB(value)

      def TiB: TiB = new TiB(value)

      def PiB: PiB = new PiB(value)
    }
  }

  private val oneByte     = BigInt(1)
  private val oneKiBBytes = oneByte << 10
  private val oneMiBBytes = oneByte << 20
  private val oneGiBBytes = oneByte << 30
  private val oneTiBBytes = oneByte << 40
  private val onePiBBytes = oneByte << 50

  def fromBytes(bytes: BigInt): Information =
    if (bytes == BigInt(0)) {
      Bytes(0)
    } else if (bytes % onePiBBytes == 0) {
      PiB(bytes / onePiBBytes)
    } else if (bytes % oneTiBBytes == 0) {
      TiB(bytes / oneTiBBytes)
    } else if (bytes % oneGiBBytes == 0) {
      GiB(bytes / oneGiBBytes)
    } else if (bytes % oneMiBBytes == 0) {
      MiB(bytes / oneMiBBytes)
    } else if (bytes % oneKiBBytes == 0) {
      KiB(bytes / oneKiBBytes)
    } else {
      Bytes(bytes)
    }

  implicit val numeric: Numeric[Information] =
    new Numeric[Information] {
      override def fromInt(x: Int) = fromBytes(x)

      override def minus(x: Information, y: Information) = fromBytes(x.bytes - y.bytes)

      override def negate(x: Information) = fromBytes(-x.bytes)

      override def plus(x: Information, y: Information) = fromBytes(x.bytes + y.bytes)

      override def times(x: Information, y: Information) = fromBytes(x.bytes * y.bytes)

      override def toDouble(x: Information): Double = x.bytes.toDouble

      override def toFloat(x: Information): Float = x.bytes.toFloat

      override def toInt(x: Information): Int = x.bytes.toInt

      override def toLong(x: Information): Long = x.bytes.toLong

      def compare(x: Information, y: Information) = x.bytes.compare(y.bytes)
    }
}

final case class Bytes(override val bytes: BigInt) extends Information {
  override def toString() = bytes + "b"
}

final case class KiB(kibibytes: BigInt) extends Information {
  override def bytes = kibibytes * 1024L

  override def toString() = kibibytes + "k"
}

final case class MiB(mebibytes: BigInt) extends Information {
  override def bytes = mebibytes * 1024L * 1024L

  def KiB: KiB = new KiB(mebibytes * 1024L)

  override def toString() = mebibytes + "m"
}

final case class GiB(gibibytes: BigInt) extends Information {
  override def bytes = gibibytes * 1024L * 1024L * 1024L

  def KiB: KiB = new KiB(gibibytes * 1024L * 1024L)

  def MiB: MiB = new MiB(gibibytes * 1024L)

  override def toString() = gibibytes + "g"
}

final case class TiB(tebibytes: BigInt) extends Information {
  override def bytes = tebibytes * 1024L * 1024L * 1024L * 1024L

  def KiB: KiB = new KiB(tebibytes * 1024L * 1024L * 1024L)

  def MiB: MiB = new MiB(tebibytes * 1024L * 1024L)

  def GiB: GiB = new GiB(tebibytes * 1024L)

  override def toString() = tebibytes + "t"
}

final case class PiB(pebibytes: BigInt) extends Information {
  override def bytes = pebibytes * 1024L * 1024L * 1024L * 1024L * 1024L

  def KiB: KiB = new KiB(pebibytes * 1024L * 1024L * 1024L * 1024L)

  def MiB: MiB = new MiB(pebibytes * 1024L * 1024L * 1024L)

  def GiB: GiB = new GiB(pebibytes * 1024L * 1024L)

  def TiB: TiB = new TiB(pebibytes * 1024L)

  override def toString() = pebibytes + "p"
}
