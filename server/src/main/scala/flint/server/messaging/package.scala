package flint
package server

import service.{ ClusterSpec, ClusterTerminationReason, ExtraTags }

import java.net.InetAddress
import java.time.{ Instant, OffsetDateTime, ZoneOffset }

import scala.concurrent.duration.FiniteDuration

import com.typesafe.scalalogging.Logger

import io.sphere.json._, generic._

import org.json4s._

import scalaz.{ NonEmptyList, Success, ValidationNel }
import scalaz.syntax.foldable._

package object messaging {
  private[messaging] type MessageValidation = ValidationNel[String, Message]

  private[messaging] def logDecodingErrors(
      logger: Logger,
      messageText: String,
      errs: NonEmptyList[String]): Unit =
    logger.error(buildDecodingErrorMessage(messageText, errs))

  private[messaging] def buildDecodingErrorMessage(
      messageText: String,
      errs: NonEmptyList[String]): String =
    s"Failed to decode message ${messageText.replaceAll("\n", "")}: " +
      errs.toList.mkString(", ")

  private[messaging] implicit val jsonBigDecimal: JSON[BigDecimal] = new JSON[BigDecimal] {
    override def read(jval: JValue): JValidation[BigDecimal] = jval match {
      case JDouble(double)   => parseDecimal(double)
      case JDecimal(decimal) => parseDecimal(decimal)
      case JLong(long)       => parseDecimal(long)
      case JInt(int)         => parseDecimal(BigDecimal(int))
      case jval              => fail("Number expected. Got " + jval.getClass.getSimpleName)
    }

    override def write(value: BigDecimal): JValue = JDecimal(value)

    private def parseDecimal(decimal: BigDecimal) =
      try {
        Success(decimal)
      } catch {
        case ex: Exception => fail("Failed to parse number: " + ex.getMessage)
      }

  }

  private[messaging] implicit val jsonInstant: JSON[Instant] = new JSON[Instant] {
    override def read(jval: JValue): JValidation[Instant] = jval match {
      case JString(s) =>
        try {
          Success(OffsetDateTime.parse(s).toInstant)
        } catch {
          case ex: Exception => fail("Failed to parse instant: " + ex.getMessage)
        }
      case jval => fail("String expected. Got " + jval.getClass.getSimpleName)
    }

    override def write(value: Instant): JValue =
      JString(value.atOffset(ZoneOffset.UTC).toString)
  }

  private[messaging] implicit val jsonDuration: JSON[FiniteDuration] = new JSON[FiniteDuration] {
    import java.time.{ Duration => JDuration }
    override def read(jval: JValue): JValidation[FiniteDuration] = jval match {
      case JString(s) =>
        try {
          Success(FiniteDuration(JDuration.parse(s).toMillis, "millis"))
        } catch {
          case ex: Exception => fail("Failed to parse duration: " + ex.getMessage)
        }
      case jval => fail("String expected. Got " + jval.getClass.getSimpleName)
    }

    override def write(value: FiniteDuration): JValue =
      JString(JDuration.ofMillis(value.toMillis).toString)
  }

  private[messaging] implicit val jsonInetAddress: JSON[InetAddress] = new JSON[InetAddress] {
    override def read(jval: JValue): JValidation[InetAddress] = jval match {
      case JString(inetAddress) =>
        try {
          Success(InetAddress.getByName(inetAddress))
        } catch {
          case ex: Exception => fail("Failed to parse ip address: " + ex.getMessage)
        }
      case jval => fail("String expected. Got " + jval.getClass.getSimpleName)
    }

    override def write(value: InetAddress): JValue = JString(value.getHostAddress)
  }

  private[messaging] implicit val jsonSpace: JSON[Space] = new JSON[Space] {
    override def read(jval: JValue): JValidation[Space] = jval match {
      case JInt(bytes)  => parseBytes(bytes)
      case JLong(bytes) => parseBytes(bytes)
      case jval         => fail("Integer expected. Got " + jval.getClass.getSimpleName)
    }

    override def write(value: Space): JValue = JInt(value.bytes)

    private def parseBytes(bytes: BigInt) =
      try {
        Success(Space.fromBytes(bytes))
      } catch {
        case ex: Exception => fail("Failed to parse space: " + ex.getMessage)
      }

  }

  private[messaging] implicit val dockerImageJson = deriveJSON[DockerImage]

  private[messaging] implicit val extraTagsJson = deriveJSON[ExtraTags]

  private[messaging] def createCaseObjectJson[T](
      typeDescription: String,
      fromString: String => T): JSON[T] =
    new JSON[T] {
      override def read(jval: JValue): JValidation[T] = jval match {
        case JString(state) => Success(fromString(state))
        case _ =>
          fail(typeDescription + " must be a string")
      }

      override def write(value: T): JValue = JString(value.toString)
    }

  private[messaging] implicit val instanceStateJson =
    createCaseObjectJson[flint.InstanceState]("Instance state", flint.InstanceState.apply)

  private[messaging] implicit val containerStateJson =
    createCaseObjectJson[ContainerState]("Container state", ContainerState.apply)

  private[messaging] implicit val clusterTerminationReasonJson =
    createCaseObjectJson[ClusterTerminationReason](
      "Cluster termination reason",
      ClusterTerminationReason.apply)

  private[messaging] implicit val clusterSpecJson = deriveJSON[ClusterSpec]

  private[messaging] implicit val subnetJson = deriveJSON[Subnet]

  private[messaging] implicit val instanceStorageSpecJson = deriveJSON[InstanceStorageSpec]

  private[messaging] implicit val instanceSpecsJson = deriveJSON[InstanceSpecs]

  private[messaging] implicit val spotPriceJson = deriveJSON[SpotPrice]

  private[messaging] implicit val instanceSnapshotJson = deriveJSON[InstanceSnapshot]

  private[messaging] implicit val clusterSnapshotJson = deriveJSON[ClusterSnapshot]
}
