package com.fortyseven.client

import cats.effect.{Effect, IO, Timer}
import com.fortyseven.commons._
import com.fortyseven.commons.config.ServiceConfig
import fs2.{Stream, StreamApp}
import io.chrisdavenport.log4cats.Logger
import monix.execution.Scheduler

class ClientProgram[F[_]: Effect: Logger] extends AppBoot[F] {

  implicit val S: Scheduler = monix.execution.Scheduler.Implicits.global

  implicit val TM: Timer[F] = Timer.derive[F](Effect[F], IO.timer(S))

  override def appStream(config: ServiceConfig): fs2.Stream[F, StreamApp.ExitCode] =
    for {
      serviceApi <- SmartHomeServiceApi.createInstance(config.host.value, config.port.value)
      _          <- Stream.eval(serviceApi.isEmpty)
    } yield StreamApp.ExitCode.Success
}

object ClientApp extends ClientProgram[IO]
