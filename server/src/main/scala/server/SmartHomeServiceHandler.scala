package com.fortyseven.server

import cats.effect.{Async, Timer}
import cats.syntax.functor._
import com.fortyseven.protocol._
import freestyle.rpc.protocol.Empty
import fs2.Stream
import io.chrisdavenport.log4cats.Logger

class SmartHomeServiceHandler[F[_]: Async: Logger: Timer: TemperatureReader: SmartHomeSupervisor]
    extends SmartHomeService[F] {
  val serviceName = "SmartHomeService"

  override def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse] =
    Logger[F].info(s"$serviceName - Request: $request").as(IsEmptyResponse(true))

  override def getTemperature(empty: Empty.type): Stream[F, Temperature] =
    for {
      _            <- Stream.eval(Logger[F].info(s"$serviceName - getTemperature Request"))
      temperatures <- TemperatureReader[F].sendSamples.take(20)
    } yield temperatures

  override def comingBackMode(request: Stream[F, Location]): Stream[F, ComingBackModeResponse] =
    for {
      _        <- Stream.eval(Logger[F].info(s"$serviceName - Enabling Coming Back Home mode"))
      location <- request
      _ <- Stream.eval(
        if (location.distanceToDestination > 0.0d)
          Logger[F]
            .info(s"$serviceName - Distance to destination: ${location.distanceToDestination} mi")
        else
          Logger[F]
            .info(s"$serviceName - You have reached your destination 🏡"))
      response <- Stream.eval(
        SmartHomeSupervisor[F].performAction(location).map(ComingBackModeResponse))
    } yield response
}
