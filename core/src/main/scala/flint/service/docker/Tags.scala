package flint
package service
package docker

import io.sphere.json.{ fromJSON, JSON }
import io.sphere.json.generic.deriveJSON

import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.HttpClient
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.HttpClients.createDefault
import org.apache.http.message.BasicHeader
import org.apache.http.protocol.BasicHttpContext

import better.files.InputStreamOps

import scalaz.{ Failure, Success, ValidationNel }
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

class Tags(
  client: HttpClient,
  authenticationServiceURLPrefix: String,
  authenticationServiceURLSuffix: String,
  registryServiceURLPrefix: String,
  registryServiceURLSuffix: String
) {
  def apply(
      repo: String,
      auth: Option[Credentials] = None
  ): ValidationNel[String, List[DockerImage]] = {

    val tokenRequest = new HttpGet(
      authenticationServiceURLPrefix
      + repo
      + authenticationServiceURLSuffix)

    auth match {
      case None => ()
      case Some(Token(key)) =>
        tokenRequest.addHeader(new BasicHeader("Authorization", s"Basic ${key}"))
      case Some(UserPass(username, password)) =>
        tokenRequest.addHeader(
          new BasicScheme().authenticate(
            new UsernamePasswordCredentials(username, password),
            tokenRequest,
            new BasicHttpContext()))
    }

    val tokenJSON = client.execute(tokenRequest).getEntity.getContent.lines.mkString

    val authToken: ValidationNel[String, String] = fromJSON[TokenResponse](tokenJSON) match {
      case Success(TokenResponse(token)) => Success(token).toValidationNel
      case Failure(errors) =>
        Failure(
          s"Failed to extract token from dockerhub server response JSON: ${tokenJSON}"
            <:: errors.map(_.toString))
    }

    authToken.flatMap { authToken =>
      val tagRequest = new HttpGet(
        registryServiceURLPrefix
        + repo
        + registryServiceURLSuffix)

      tagRequest.setHeader("Authorization", s"Bearer ${authToken}")

      val tagJSON = client.execute(tagRequest).getEntity.getContent.lines.mkString

      fromJSON[TagResponse](tagJSON) match {
        case Success(TagResponse(name, tags)) =>
          Success(tags.map(DockerImage(name, _))).toValidationNel
        case Failure(errors) =>
          Failure(
            s"Failed to extract tags from dockerhub server response JSON: ${tagJSON}"
              <:: errors.map(_.toString))
      }
    }
  }

}

object Tags {
  def make: Tags = new Tags(
    createDefault,
    "https://auth.docker.io/token?service=registry.docker.io&scope=repository:",
    ":pull",
    "https://registry.hub.docker.com/v2/",
    "/tags/list"
    )
}
