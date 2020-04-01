package fsclient.utils

import java.nio.charset.StandardCharsets
import java.util.Base64

import fsclient.entities.OAuthVersion.Version2
import org.apache.http.entity.ContentType
import org.http4s.Header

object FsHeaders {
  def accept(contentType: ContentType): Header = Header("accept", contentType.getMimeType)
  def userAgent(value: String): Header = Header("User-Agent", value)
  // https://tools.ietf.org/html/rfc7617
  def authorizationBasic(secret: String): Header = {
    val base64Secret = Base64.getEncoder.encodeToString(secret.getBytes(StandardCharsets.UTF_8))
    Header("Authorization", s"Basic $base64Secret")
  }
  // https://tools.ietf.org/html/rfc6750
  def authorizationBearer(token: Version2.AccessToken): Header =
    Header("Authorization", s"Bearer ${token.value}")
}
