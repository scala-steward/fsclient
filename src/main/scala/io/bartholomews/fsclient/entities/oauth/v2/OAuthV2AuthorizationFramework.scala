package io.bartholomews.fsclient.entities.oauth.v2

import cats.data.Chain
import io.bartholomews.fsclient.entities.oauth.{AuthorizationCode, NonRefreshableToken}
import io.bartholomews.fsclient.requests.JsonRequest
import io.bartholomews.fsclient.utils.FsHeaders
import io.circe.Decoder
import io.circe.generic.extras.semiauto.deriveUnwrappedDecoder
import org.apache.http.entity.ContentType
import org.http4s.{Header, Headers, Uri, UrlForm}

// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//
//  The OAuth 2.0 Authorization Framework
//
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// https://tools.ietf.org/html/rfc6749
object OAuthV2AuthorizationFramework {

  sealed trait SignerType

  // https://tools.ietf.org/html/rfc6749#section-2.3.1
  case class ClientPassword(clientId: ClientId, clientSecret: ClientSecret) {
    lazy val authorizationBasic: Header = FsHeaders.authorizationBasic(s"${clientId.value}:${clientSecret.value}")
  }
  case class ClientId(value: String) extends AnyVal
  case class ClientSecret(value: String) extends AnyVal
  // https://tools.ietf.org/html/rfc6749#section-3.1.2
  case class RedirectUri(value: Uri)

  case class AccessToken(value: String) extends AnyVal
  object AccessToken { implicit val decoder: Decoder[AccessToken] = deriveUnwrappedDecoder }

  case class RefreshToken(value: String) extends AnyVal
  object RefreshToken { implicit val decoder: Decoder[RefreshToken] = deriveUnwrappedDecoder }

  private def authorizationUri(
    responseType: String,
    clientId: ClientId,
    redirectUri: Uri,
    state: Option[String],
    scopes: List[String]
  )(serverUri: Uri): Uri =
    serverUri
      .withQueryParam("client_id", clientId.value)
      .withQueryParam("response_type", responseType)
      .withQueryParam("redirect_uri", redirectUri.renderString)
      .withOptionQueryParam("state", state)
      .withOptionQueryParam("scope", if (scopes.isEmpty) None else Some(scopes.mkString(" ")))

  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  //  Authorization Code Grant
  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // https://tools.ietf.org/html/rfc6749#section-4.1
  case object AuthorizationCodeGrant extends SignerType {

    // https://tools.ietf.org/html/rfc6749#section-4.1.1
    def authorizationCodeUri(clientId: ClientId, redirectUri: Uri, state: Option[String], scopes: List[String])(
      serverUri: Uri
    ): Uri = authorizationUri(responseType = "code", clientId, redirectUri, state, scopes)(serverUri)

    // https://tools.ietf.org/html/rfc6749#section-4.1.3
    abstract class AccessTokenRequest(
      code: String,
      clientPassword: ClientPassword,
      maybeRedirectUri: Option[RedirectUri]
    ) extends JsonRequest.Post[UrlForm, AuthorizationCode] {
      override val headers: Headers = Headers.of(
        clientPassword.authorizationBasic,
        FsHeaders.contentType(ContentType.APPLICATION_FORM_URLENCODED)
      )
      final override lazy val entityBody = UrlForm(
        Map(
          "grant_type" -> Chain("authorization_code"),
          "code" -> Chain(code),
          "redirect_uri" -> maybeRedirectUri.fold(Chain.empty[String])(uri => Chain.one(uri.value.renderString))
        )
      )
    }

    // https://tools.ietf.org/html/rfc6749#section-6
    abstract class RefreshTokenRequest(refreshToken: RefreshToken, clientPassword: ClientPassword, scopes: List[String])
        extends JsonRequest.Post[UrlForm, AuthorizationCode] {
      override val headers: Headers = Headers.of(
        clientPassword.authorizationBasic,
        FsHeaders.contentType(ContentType.APPLICATION_FORM_URLENCODED)
      )
      final override val entityBody = UrlForm(
        Map(
          "grant_type" -> Chain("refresh_token"),
          "refresh_token" -> Chain(refreshToken.value),
          // TODO: test behaviour of Chain.seq, make sure it doesn't discard the tail,
          //  otherwise you need to mkString first
          "scope" -> Chain.fromSeq(scopes)
        )
      )
    }
  }

  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // Implicit Grant
  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // https://tools.ietf.org/html/rfc6749#section-4.2
  case object ImplicitGrant extends SignerType {
    // https://tools.ietf.org/html/rfc6749#section-4.2.1
    def authorizationTokenUri(clientId: ClientId, redirectUri: Uri, state: Option[String], scopes: List[String])(
      serverUri: Uri
    ): Uri = authorizationUri(responseType = "token", clientId, redirectUri, state, scopes)(serverUri)
  }

  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // Client Credentials Grant
  // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
  // https://tools.ietf.org/html/rfc6749#section-4.4
  case object ClientCredentialsGrant extends SignerType {
    // https://tools.ietf.org/html/rfc6749#section-4.4.2
    abstract class AccessTokenRequest(clientPassword: ClientPassword)
        extends JsonRequest.Post[UrlForm, NonRefreshableToken] {
      final override val entityBody = UrlForm(("grant_type", "client_credentials"))
      override val headers: Headers = Headers.of(
        clientPassword.authorizationBasic,
        FsHeaders.contentType(ContentType.APPLICATION_FORM_URLENCODED)
      )
    }
  }
}