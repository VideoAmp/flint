package flint
package server

import service.ClusterSpec

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
      case JDecimal(decimal) =>
        try {
          Success(decimal)
        } catch {
          case ex: Exception => fail("Failed to parse decimal: " + ex.getMessage)
        }
      case _ => fail("Number expected")
    }

    override def write(value: BigDecimal): JValue = JDecimal(value)
  }

  private[messaging] implicit val jsonInstant: JSON[Instant] = new JSON[Instant] {
    override def read(jval: JValue): JValidation[Instant] = jval match {
      case JString(s) =>
        try {
          Success(OffsetDateTime.parse(s).toInstant)
        } catch {
          case ex: Exception => fail("Failed to parse instant: " + ex.getMessage)
        }
      case _ => fail("String expected")
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
      case _ => fail("String expected")
    }

    override def write(value: FiniteDuration): JValue =
      JString(JDuration.ofMillis(value.toMillis).toString)
  }

  private[messaging] implicit val jsonSpace: JSON[Space] = new JSON[Space] {
    override def read(jval: JValue): JValidation[Space] = jval match {
      case JInt(bytes) =>
        try {
          Success(Space.fromBytes(bytes))
        } catch {
          case ex: Exception => fail("Failed to parse space: " + ex.getMessage)
        }
      case _ => fail("Integer expected")
    }

    override def write(value: Space): JValue = JInt(value.bytes)
  }

  private[messaging] implicit val dockerImageJson = deriveJSON[DockerImage]

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

  private[messaging] implicit val lifecycleStateJson =
    createCaseObjectJson[LifecycleState]("Lifecycle state", LifecycleState.apply)

  private[messaging] implicit val containerStateJson =
    createCaseObjectJson[ContainerState]("Container state", ContainerState.apply)

  private[messaging] implicit val terminationReasonJson =
    createCaseObjectJson[TerminationReason]("Termination reason", TerminationReason.apply)

  private[messaging] implicit val clusterSpecJson = deriveJSON[ClusterSpec]

  private[messaging] implicit val instanceStorageSpecJson = deriveJSON[InstanceStorageSpec]

  private[messaging] implicit val instanceSpecsJson = deriveJSON[InstanceSpecs]

  private[messaging] implicit val spotPriceJson = deriveJSON[SpotPrice]

  private[messaging] implicit val instanceSnapshotJson = deriveJSON[InstanceSnapshot]

  private[messaging] implicit val clusterSnapshotJson = deriveJSON[ClusterSnapshot]
}
