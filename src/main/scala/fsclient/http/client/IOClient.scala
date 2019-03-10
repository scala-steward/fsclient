package fsclient.http.client

import cats.effect.{ContextShift, IO, Resource}
import fsclient.config.OAuthConsumer
import fsclient.entities.{HttpEndpoint, HttpResponse, OAuthAccessToken, ResponseError}
import fsclient.http.effect.HttpEffectClient
import io.circe.Decoder
import org.http4s.Headers
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.oauth1.Consumer

import scala.concurrent.ExecutionContext

class IOClient(override val consumer: OAuthConsumer,
               private[http] val accessToken: Option[OAuthAccessToken] = None)
              (implicit ec: ExecutionContext) extends HttpEffectClient[IO] {

  private[http] implicit val ioContextShift: ContextShift[IO] = IO.contextShift(ec)
  private[http] implicit val httpConsumer: Consumer = Consumer(this.consumer.key, this.consumer.secret)
  private[http] implicit val resource: Resource[IO, Client[IO]] = BlazeClientBuilder[IO](ec).resource

  override private[http] def run[A]: fs2.Stream[IO, HttpResponse[A]] => IO[HttpResponse[A]] = _
    .compile
    .last
    .flatMap(_.toRight(ResponseError.empty).fold(
      empty => IO.pure(HttpResponse(Headers(), Left(empty))),
      value => IO.pure(value)
    ))

  def fetchJson[A](endpoint: HttpEndpoint[A])(implicit decode: Decoder[A]): IOResponse[A] =
    fetchJson(endpoint.uri, endpoint.method, accessToken)

  def fetchPlainText[A](endpoint: HttpEndpoint[A])(implicit decoder: HttpPipe[IO, String, A]): IOResponse[A] =
    fetchPlainText(endpoint.uri, endpoint.method, accessToken)
}
