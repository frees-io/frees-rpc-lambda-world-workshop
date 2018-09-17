package com.fortyseven.server

import cats.effect.Sync
import cats.syntax.functor._
import com.fortyseven.protocol.{IsEmptyRequest, IsEmptyResponse, SmartHomeService}
import io.chrisdavenport.log4cats.Logger

class SmartHomeServiceHandler[F[_]: Sync: Logger] extends SmartHomeService[F] {
  val serviceName = "SmartHomeService"

  override def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse] =
    Logger[F].info(s"$serviceName - Request: $request").as(IsEmptyResponse(true))

}
