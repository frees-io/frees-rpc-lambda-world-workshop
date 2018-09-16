package com.fortyseven.server

import cats.effect._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.fortyseven.commons._
import com.fortyseven.commons.config.ServiceConfig
import com.fortyseven.protocol.PeopleService
import freestyle.rpc.server.{AddService, GrpcConfig, GrpcServer}
import fs2.{Stream, StreamApp}
import io.chrisdavenport.log4cats.Logger
import monix.execution.Scheduler

class ServerProgram[F[_]: Effect: Logger] extends AppBoot[F] {

  implicit val S: Scheduler = monix.execution.Scheduler.Implicits.global

  implicit val TM: Timer[F] = Timer.derive[F](Effect[F], IO.timer(S))

  override def appStream(config: ServiceConfig): fs2.Stream[F, StreamApp.ExitCode] = {

    implicit val PS: PeopleService[F] = new PeopleServiceHandler[F]

    val grpcConfigs: List[GrpcConfig] = List(AddService(PeopleService.bindService[F]))

    Stream.eval(
      for {
        server <- GrpcServer.default[F](config.port.value, grpcConfigs)
        _ <- Logger[F].info(
          s"${config.name.value} - Starting app.server at ${config.host.value}:${config.port.value}")
        exitCode <- GrpcServer.server(server).as(StreamApp.ExitCode.Success)
      } yield exitCode
    )
  }
}

object ServerApp extends ServerProgram[IO]
