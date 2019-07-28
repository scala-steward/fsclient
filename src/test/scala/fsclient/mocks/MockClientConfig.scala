package fsclient.mocks

import fsclient.config.OAuthConsumer
import fsclient.entities.AccessToken
import fsclient.http.client.{IOAuthClient, IOSimpleClient}
import org.http4s.client.oauth1.Token

trait MockClientConfig {

  import scala.concurrent.ExecutionContext.Implicits.global

  final val validConsumerKey = "VALID_CONSUMER_KEY"
  final val validConsumerSecret = "VALID_CONSUMER_SECRET"

  final val invalidConsumerKey = "INVALID_CONSUMER_KEY"
  final val invalidConsumerSecret = "INVALID_CONSUMER_SECRET"

  final val validOAuthTokenValue = "VALID_OAUTH_TOKEN_VALUE"
  final val validOAuthTokenSecret = "VALID_OAUTH_TOKEN_SECRET"
  final val validOAuthVerifier = "OAUTH_VERIFIER"

  final val validToken = Token(validOAuthTokenValue, validOAuthTokenSecret)

  final val validOAuthAccessToken: AccessToken = AccessToken(
    Token(validOAuthTokenValue, validOAuthTokenSecret)
  )
  // override val verifier: Option[String] = Some(validOAuthVerifier)

  def validSimpleClient: IOSimpleClient = simpleClientWith(validConsumerKey, validConsumerSecret)

  def validOAuthClient: IOAuthClient = oAuthClientWith(validConsumerKey, validConsumerSecret, validOAuthAccessToken)

  def simpleClientWith(key: String,
                       secret: String,
                       appName: String = "someApp",
                       appVersion: Option[String] = Some("1.0"),
                       appUrl: Option[String] = Some("app.git")): IOSimpleClient = new IOSimpleClient(
    OAuthConsumer(appName, appVersion, appUrl, key, secret))

  def oAuthClientWith(key: String,
                      secret: String,
                      accessToken: AccessToken,
                      appName: String = "someApp",
                      appVersion: Option[String] = Some("1.0"),
                      appUrl: Option[String] = Some("app.git")): IOAuthClient = new IOAuthClient(
    OAuthConsumer(appName, appVersion, appUrl, key, secret), accessToken)
}