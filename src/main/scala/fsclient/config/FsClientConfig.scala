package fsclient.config

import fsclient.entities.AuthVersion.V1
import fsclient.entities._
import org.http4s.client.oauth1.Consumer
import pureconfig.{ConfigReader, ConfigSource, Derivation}

case class FsClientConfig[A <: AuthInfo](userAgent: UserAgent, authInfo: A)

object FsClientConfig {

  import pureconfig.generic.auto._

  private def v1(consumerConfig: ConsumerConfig): FsClientConfig[AuthEnabled] = {
    val signer: Signer = V1.BasicSignature(Consumer(consumerConfig.key, consumerConfig.secret))
    new FsClientConfig(
      UserAgent(consumerConfig.appName, consumerConfig.appVersion, consumerConfig.appUrl),
      AuthEnabled(signer)
    )
  }

  def v1(userAgent: UserAgent, consumer: Consumer): FsClientConfig[AuthEnabled] = {
    val signer: Signer = V1.BasicSignature(consumer)
    FsClientConfig(userAgent, AuthEnabled(signer))
  }

  def v1(): FsClientConfig[AuthEnabled] = FsClientConfig.v1(ConfigSource.default.loadOrThrow[Config].consumer)

  def v1(key: String): FsClientConfig[AuthEnabled] = {
    implicit val customConfigReader: Derivation[ConfigReader[Config]] = Derivations.withCustomKey(key)
    FsClientConfig.v1(ConfigSource.default.loadOrThrow[Config].consumer)
  }

  def disabled(userAgent: UserAgent): FsClientConfig[AuthDisabled.type] = FsClientConfig(userAgent, AuthDisabled)

  private[fsclient] case class Config(consumer: ConsumerConfig, logger: LoggerConfig)

  private[fsclient] case class ConsumerConfig(
    appName: String,
    appVersion: Option[String],
    appUrl: Option[String],
    key: String,
    secret: String
  )

  private[fsclient] case class LoggerConfig(name: String)
}