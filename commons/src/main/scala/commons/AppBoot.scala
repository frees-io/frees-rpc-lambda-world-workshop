package com.fortyseven.commons

import cats.effect.Effect
import com.fortyseven.commons.config.{ServiceConfig, ServiceConfigReader}
import fs2.{Stream, StreamApp}

abstract class AppBoot[F[_]: Effect] extends StreamApp[F] {

  override def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, StreamApp.ExitCode] =
    for {
      config   <- ServiceConfigReader[F].serviceConfig[ServiceConfig]
      exitCode <- appStream(config)
    } yield exitCode

  def appStream(config: ServiceConfig): Stream[F, StreamApp.ExitCode]
}
