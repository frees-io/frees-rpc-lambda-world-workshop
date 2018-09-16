package com.fortyseven.client

import cats.effect.{Effect, IO, Timer}
import cats.instances.list._
import cats.syntax.traverse._
import com.fortyseven.commons._
import com.fortyseven.commons.config.ServiceConfig
import com.fortyseven.protocol.Person
import fs2.{Stream, StreamApp}
import io.chrisdavenport.log4cats.Logger
import monix.execution.Scheduler

class ClientProgram[F[_]: Effect: Logger] extends AppBoot[F] {

  implicit val S: Scheduler = monix.execution.Scheduler.Implicits.global

  implicit val TM: Timer[F] = Timer.derive[F](Effect[F], IO.timer(S))

  override def appStream(config: ServiceConfig): fs2.Stream[F, StreamApp.ExitCode] =
    for {
      peopleApi <- PeopleServiceApi.createInstance(config.host.value, config.port.value)
      exitCode <- Stream
        .eval(List("foo", "bar", "baz").traverse[F, Person](peopleApi.getPersonByName))
        .as(StreamApp.ExitCode.Success)
    } yield exitCode
}

object ClientApp extends ClientProgram[IO]
