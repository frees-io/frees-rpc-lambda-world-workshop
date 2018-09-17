package com.fortyseven.server

import cats.effect.{Async, Timer}
import cats.syntax.functor._
import com.fortyseven.protocol._
import freestyle.rpc.protocol.Empty
import fs2.Stream
import io.chrisdavenport.log4cats.Logger

class SmartHomeServiceHandler[F[_]: Async: Logger: Timer: TemperatureReader]
    extends SmartHomeService[F] {
  val serviceName = "SmartHomeService"

  override def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse] =
    Logger[F].info(s"$serviceName - Request: $request").as(IsEmptyResponse(true))

  override def getTemperature(empty: Empty.type): Stream[F, Temperature] =
    for {
      _            <- Stream.eval(Logger[F].info(s"$serviceName - getTemperature Request"))
      temperatures <- TemperatureReader[F].sendSamples.take(20)
    } yield temperatures
}
