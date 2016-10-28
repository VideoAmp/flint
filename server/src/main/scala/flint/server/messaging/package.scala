package flint
package server

import com.typesafe.scalalogging.Logger

import scalaz.{ NonEmptyList, ValidationNel }
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
}
