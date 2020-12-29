package io.bartholomews.fsclient.core.oauth.v2

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, post, stubFor, urlMatching}
import io.bartholomews.fsclient.client.ClientData.{
  sampleAuthorizationCode,
  sampleClientPassword,
  sampleRedirectUri,
  sampleRefreshToken,
  sampleUserAgent
}
import io.bartholomews.fsclient.client.IdentityClient
import io.bartholomews.fsclient.core.FsClient
import io.bartholomews.fsclient.core.oauth.v2.OAuthV2.{
  AccessToken,
  AuthorizationCodeGrant,
  ClientCredentialsGrant,
  ImplicitGrant,
  ResponseHandler
}
import io.bartholomews.fsclient.core.oauth.{AccessTokenSigner, ClientPasswordAuthentication, NonRefreshableTokenSigner}
import io.bartholomews.fsclient.wiremock.WiremockServer
import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.client.circe.asJson
import sttp.client.{Identity, UriContext}
import sttp.model.Uri
import sttp.model.Uri.QuerySegment
import sttp.model.Uri.QuerySegment.KeyValue

class OAuthV2Test extends AnyWordSpec with IdentityClient with WiremockServer with Matchers with Inside {

  "OAuthV2" when {

    val signer = ClientPasswordAuthentication(sampleClientPassword)

    val client: FsClient[Identity, ClientPasswordAuthentication] = FsClient.v2.clientPassword(
      sampleUserAgent,
      signer
    )

    "AuthorizationCodeGrant" should {

      val authorizationCodeRequest = AuthorizationCodeRequest(
        clientId = client.signer.clientPassword.clientId,
        redirectUri = sampleRedirectUri,
        state = None,
        scopes = List.empty
      )

      val authorizationRequestUri: Uri =
        AuthorizationCodeGrant.authorizationRequestUri(
          authorizationCodeRequest,
          serverUri = uri"$wiremockBaseUri/authorization-server/code"
        )

      "set the correct host and path" in {
        authorizationRequestUri.copy(querySegments =
          List.empty
        ) shouldBe uri"$wiremockBaseUri/authorization-server/code"
      }

      "set the correct query params" in {
        authorizationRequestUri.querySegments.toList shouldBe List(
          KeyValue("redirect_uri", sampleRedirectUri.value.toString),
          KeyValue("client_id", sampleClientPassword.clientId.value),
          KeyValue("response_type", "code")
        )
      }

      "return an error if user denies permissions" in {

        val uriRedirect = sampleRedirectUri.value.querySegment(
          QuerySegment.KeyValue("error", "temporarily_unavailable")
        )

        AuthorizationCodeGrant.authorizationResponse(
          request = authorizationCodeRequest,
          redirectionUriResponse = uriRedirect
        ) shouldBe Left("temporarily_unavailable")
      }

      "return the authorization code if user accepts permissions" in {

        val uriRedirect = sampleRedirectUri.value.querySegment(
          QuerySegment.KeyValue("code", sampleAuthorizationCode.value)
        )

        AuthorizationCodeGrant.authorizationResponse(
          request = authorizationCodeRequest,
          redirectionUriResponse = uriRedirect
        ) shouldBe Right(sampleAuthorizationCode)
      }

      "return an access token" in {

        implicit val handleResponse: ResponseHandler[AccessTokenSigner] = asJson[AccessTokenSigner]

        import java.net.URLEncoder.encode

        val expectedRequestBody = List(
          "grant_type=authorization_code",
          s"code=${sampleAuthorizationCode.value}",
          s"redirect_uri=${encode(sampleRedirectUri.value.toString, "UTF-8")}"
        ).mkString("&")

        stubFor(
          post(urlMatching("/authorization-server/authorize"))
            .withRequestBody(equalTo(expectedRequestBody))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("auth/authorization_access.json")
            )
        )

        val response = AuthorizationCodeGrant
          .accessTokenRequest(
            serverUri = uri"$wiremockBaseUri/authorization-server/authorize",
            sampleAuthorizationCode,
            Some(sampleRedirectUri),
            sampleClientPassword
          )
          .send()

        inside(response.body) { case Right(accessTokenSigner) =>
          accessTokenSigner.accessToken shouldBe AccessToken("some-access-token")
        }
      }

      "return a refresh token" in {

        implicit val handleResponse: ResponseHandler[AccessTokenSigner] = asJson[AccessTokenSigner]

        val expectedRequestBody = List(
          "grant_type=refresh_token",
          s"refresh_token=${sampleRefreshToken.value}"
        ).mkString("&")

        stubFor(
          post(urlMatching("/authorization-server/refresh"))
            .withRequestBody(equalTo(expectedRequestBody))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("auth/authorization_access.json")
            )
        )

        val response = AuthorizationCodeGrant
          .refreshTokenRequest(
            serverUri = uri"$wiremockBaseUri/authorization-server/refresh",
            sampleRefreshToken,
            scopes = List.empty,
            sampleClientPassword
          )
          .send()

        inside(response.body) { case Right(accessTokenSigner) =>
          accessTokenSigner.accessToken shouldBe AccessToken("some-access-token")
        }
      }
    }

    "ImplicitGrant" should {

      val authorizationTokenRequest = AuthorizationTokenRequest(
        clientId = client.signer.clientPassword.clientId,
        redirectUri = sampleRedirectUri,
        state = None,
        scopes = List.empty
      )

      val authorizationRequestUri: Uri =
        ImplicitGrant.authorizationRequestUri(
          authorizationTokenRequest,
          serverUri = uri"$wiremockBaseUri/authorization-server/token"
        )

      "set the correct host and path" in {
        authorizationRequestUri.copy(querySegments =
          List.empty
        ) shouldBe uri"$wiremockBaseUri/authorization-server/token"
      }

      "set the correct query params" in {
        authorizationRequestUri.querySegments.toList shouldBe List(
          KeyValue("redirect_uri", sampleRedirectUri.value.toString),
          KeyValue("client_id", sampleClientPassword.clientId.value),
          KeyValue("response_type", "token")
        )
      }

      "return an error if user denies permissions" in {

        val uriRedirect = sampleRedirectUri.value.querySegment(
          QuerySegment.KeyValue("error", "temporarily_unavailable")
        )

        ImplicitGrant.accessTokenResponse(
          request = authorizationTokenRequest,
          redirectionUriResponse = uriRedirect
        ) shouldBe Left("temporarily_unavailable")
      }

      "return the authorization code if user accepts permissions" in {

        val uriRedirect = sampleRedirectUri.value.params(
          ("access_token", "implicit-grant-access-token"),
          ("token_type", "bearer"),
          ("expires_in", "3600")
        )

        val response = ImplicitGrant.accessTokenResponse(
          request = authorizationTokenRequest,
          redirectionUriResponse = uriRedirect
        )

        inside(response) { case Right(nonRefreshableTokenSigner) =>
          nonRefreshableTokenSigner.accessToken shouldBe AccessToken("implicit-grant-access-token")
        }
      }
    }

    "ClientCredentialsGrant" should {

      implicit val handleResponse: ResponseHandler[NonRefreshableTokenSigner] =
        asJson[NonRefreshableTokenSigner]

      val accessTokenRequest =
        ClientCredentialsGrant.accessTokenRequest(
          serverUri = uri"$wiremockBaseUri/authorization-server/token",
          sampleClientPassword
        )

      "return an access token" in {
        stubFor(
          post(urlMatching("/authorization-server/token"))
            .withRequestBody(equalTo("grant_type=client_credentials"))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBodyFile("auth/client_credentials.json")
            )
        )

        inside(accessTokenRequest.send().body) { case Right(nonRefreshableTokenSigner) =>
          nonRefreshableTokenSigner.accessToken shouldBe AccessToken("some-access-token")
        }
      }
    }
  }
}
