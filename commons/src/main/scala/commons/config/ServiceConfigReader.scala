package com.fortyseven.commons.config

import cats.effect.Sync
import cats.syntax.either._
import fs2.Stream
import pureconfig.{ConfigReader, Derivation}

trait ServiceConfigReader[F[_]] {
  def serviceConfig[Config](implicit reader: Derivation[ConfigReader[Config]]): Stream[F, Config]
}

object ServiceConfigReader {
  def apply[F[_]: Sync]: ServiceConfigReader[F] = new ServiceConfigReader[F] {
    override def serviceConfig[Config](
        implicit reader: Derivation[ConfigReader[Config]]): Stream[F, Config] =
      Stream.eval(
        Sync[F].fromEither(
          pureconfig
            .loadConfig[Config]
            .leftMap(e => new IllegalStateException(s"Error loading configuration: $e"))
        ))
  }
}
