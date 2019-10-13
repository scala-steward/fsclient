package fsclient.http.client

import cats.effect.{ContextShift, IO, Resource}
import fsclient.config.OAuthConsumer
import fsclient.entities._
import fsclient.http.effect.HttpEffectClient
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.oauth1.Consumer
import org.http4s.{Headers, Status}

import scala.concurrent.ExecutionContext

abstract private[http] class IOClient(override val consumer: OAuthConsumer)(implicit ec: ExecutionContext)
    extends HttpEffectClient[IO] {

  type IOHttpPipe[A, B] = HttpPipe[IO, A, B]

  implicit private[http] val ioContextShift: ContextShift[IO] =
    IO.contextShift(ec)
  implicit private[http] val resource: Resource[IO, Client[IO]] =
    BlazeClientBuilder[IO](ec).resource

  final override def run[A]: fs2.Stream[IO, HttpResponse[A]] => IO[HttpResponse[A]] =
    _.compile.last
      .flatMap(
        _.toRight(ResponseError.apply(GenericResponseError, Status.InternalServerError)).fold(
          error => IO.pure(HttpResponse(Headers.empty, Left(error))),
          value => IO.pure(value)
        )
      )

  implicit private[http] val httpConsumer: Consumer =
    Consumer(consumer.key, consumer.secret)
}
