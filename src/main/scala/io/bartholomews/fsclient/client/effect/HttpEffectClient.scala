package io.bartholomews.fsclient.client.effect

import cats.effect.{Effect, Resource}
import cats.implicits._
import fs2.Pipe
import io.bartholomews.fsclient.codecs.RawDecoder
import io.bartholomews.fsclient.config.FsClientConfig
import io.bartholomews.fsclient.entities._
import io.bartholomews.fsclient.utils.HttpTypes.HttpResponse
import org.http4s._
import org.http4s.client.Client

trait HttpEffectClient[F[_], OAuth <: OAuthInfo] extends RequestF {

  def appConfig: FsClientConfig[OAuth]

  implicit def resource: Resource[F, Client[F]]

  private def execute[A](
    authInfo: OAuthInfo
  )(implicit f: Effect[F]): fs2.Stream[F, HttpResponse[A]] => F[HttpResponse[A]] =
    _.compile.last.flatMap(maybeResponse =>
      f.pure(maybeResponse.getOrElse(FsResponse(authInfo, EmptyResponseException())))
    )

  private[fsclient] def fetch[Raw, Res](
    implicit
    f: Effect[F],
    request: Request[F],
    authInfo: OAuthInfo,
    rawDecoder: RawDecoder[Raw],
    decode: Pipe[F, Raw, Res]
  ): F[FsResponse[HttpError, Res]] =
    resource.use { client =>
      execute(authInfo)(f)(signAndProcessRequest[F, Raw, Res](this, client))
    }
}
