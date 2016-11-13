package flint

sealed trait Space {
  import Space.fromBytes

  def bytes: BigInt

  def *(multiple: Int): Space = fromBytes(bytes * multiple)

  def *(multiple: Long): Space = fromBytes(bytes * multiple)
}

object Space {
  object Implicits {
    implicit class IntSpace(value: Int) {
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

  def fromBytes(bytes: BigInt): Space =
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

  implicit val numeric: Numeric[Space] =
    new Numeric[Space] {
      override def fromInt(x: Int) = fromBytes(x)

      override def minus(x: Space, y: Space) = fromBytes(x.bytes - y.bytes)

      override def negate(x: Space) = fromBytes(-x.bytes)

      override def plus(x: Space, y: Space) = fromBytes(x.bytes + y.bytes)

      override def times(x: Space, y: Space) = fromBytes(x.bytes * y.bytes)

      override def toDouble(x: Space): Double = x.bytes.toDouble

      override def toFloat(x: Space): Float = x.bytes.toFloat

      override def toInt(x: Space): Int = x.bytes.toInt

      override def toLong(x: Space): Long = x.bytes.toLong

      def compare(x: Space, y: Space) = x.bytes.compare(y.bytes)
    }
}

final case class Bytes(override val bytes: BigInt) extends Space {
  override def toString() = bytes + "b"
}

final case class KiB(kibibytes: BigInt) extends Space {
  override def bytes = kibibytes * 1024L

  override def toString() = kibibytes + "k"
}

final case class MiB(mebibytes: BigInt) extends Space {
  override def bytes = mebibytes * 1024L * 1024L

  def KiB: KiB = new KiB(mebibytes * 1024L)

  override def toString() = mebibytes + "m"
}

final case class GiB(gibibytes: BigInt) extends Space {
  override def bytes = gibibytes * 1024L * 1024L * 1024L

  def KiB: KiB = new KiB(gibibytes * 1024L * 1024L)

  def MiB: MiB = new MiB(gibibytes * 1024L)

  override def toString() = gibibytes + "g"
}

final case class TiB(tebibytes: BigInt) extends Space {
  override def bytes = tebibytes * 1024L * 1024L * 1024L * 1024L

  def KiB: KiB = new KiB(tebibytes * 1024L * 1024L * 1024L)

  def MiB: MiB = new MiB(tebibytes * 1024L * 1024L)

  def GiB: GiB = new GiB(tebibytes * 1024L)

  override def toString() = tebibytes + "t"
}

final case class PiB(pebibytes: BigInt) extends Space {
  override def bytes = pebibytes * 1024L * 1024L * 1024L * 1024L * 1024L

  def KiB: KiB = new KiB(pebibytes * 1024L * 1024L * 1024L * 1024L)

  def MiB: MiB = new MiB(pebibytes * 1024L * 1024L * 1024L)

  def GiB: GiB = new GiB(pebibytes * 1024L * 1024L)

  def TiB: TiB = new TiB(pebibytes * 1024L)

  override def toString() = pebibytes + "p"
}
