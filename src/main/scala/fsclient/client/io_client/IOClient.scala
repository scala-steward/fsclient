package fsclient.client.io_client

import cats.effect.{ContextShift, IO, Resource}
import fsclient.client.effect.HttpEffectClient
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

trait IOClient extends HttpEffectClient[IO] {

  implicit def ec: ExecutionContext

  implicit val ioContextShift: ContextShift[IO] =
    IO.contextShift(ec)

  // FIXME: Can/Should be moved to HttpEffectClient ?
  implicit override val resource: Resource[IO, Client[IO]] =
    BlazeClientBuilder[IO](ec).resource
}