package flint
package docker

import com.typesafe.scalalogging.LazyLogging

import io.sphere.json.{ fromJSON, JSON, JSONError }
import io.sphere.json.generic.deriveJSON

import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.message.BasicHeader
import org.apache.http.protocol.BasicHttpContext

import better.files.InputStreamOps

import scalaz.{ Failure, NonEmptyList, Success, ValidationNel }
import scalaz.Validation.FlatMap.ValidationFlatMapRequested

case class TagResponse(name: String, tags: List[String])
object TagResponse {
  implicit val json: JSON[TagResponse] = deriveJSON[TagResponse]
}

case class TokenResponse(token: String)
object TokenResponse {
  implicit val json: JSON[TokenResponse] = deriveJSON[TokenResponse]
}

sealed trait Credentials
case class UserPass(username: String, password: String) extends Credentials
case class Token(key: String)                           extends Credentials

class Tags(client: HttpClient) extends LazyLogging {
  import Tags._

  def apply(
      repo: String,
      auth: Option[Credentials] = None
  ): ValidationNel[String, List[DockerImage]] = {

    val tokenRequest = new HttpGet(
      s"https://auth.docker.io/token?service=registry.docker.io&scope=repository:$repo:pull")

    auth match {
      case None => ()
      case Some(Token(key)) =>
        tokenRequest.addHeader(new BasicHeader("Authorization", s"Basic $key"))
      case Some(UserPass(username, password)) =>
        tokenRequest.addHeader(
          new BasicScheme().authenticate(
            new UsernamePasswordCredentials(username, password),
            tokenRequest,
            new BasicHttpContext()))
    }

    logger.trace(s"Requesting docker registry auth token with request $tokenRequest")

    val tokenJSON =
      try {
        client.execute(tokenRequest).getEntity.getContent.lines.mkString
      } catch {
        case ex: Exception =>
          logger.error("Docker registry auth token request failed", ex)
          throw ex
      }

    val authToken: ValidationNel[String, String] = fromJSON[TokenResponse](tokenJSON) match {
      case Success(TokenResponse(token)) =>
        logger.trace(s"Received docker registry auth token $token")
        Success(token).toValidationNel
      case Failure(errors) =>
        failWithErrors(
          s"Failed to extract token from Docker registry response JSON: $tokenJSON",
          errors)
    }

    authToken.flatMap { authToken =>
      val tagRequest = new HttpGet(s"https://registry.hub.docker.com/v2/$repo/tags/list")

      tagRequest.setHeader("Authorization", s"Bearer $authToken")

      val tagJSON = client.execute(tagRequest).getEntity.getContent.lines.mkString

      fromJSON[TagResponse](tagJSON) match {
        case Success(TagResponse(name, tags)) =>
          Success(tags.map(DockerImage(name, _))).toValidationNel
        case Failure(errors) =>
          failWithErrors(
            s"Failed to extract tags from Docker registry response JSON: $tagJSON",
            errors)
      }
    }
  }
}

private object Tags extends LazyLogging {
  def failWithErrors[A](
      message: String,
      errors: NonEmptyList[JSONError]): ValidationNel[String, A] = {
    val failureMessage = message <:: errors.map(_.toString)
    logger.error(failureMessage.list.toList.mkString(", "))
    Failure(failureMessage)
  }
}
