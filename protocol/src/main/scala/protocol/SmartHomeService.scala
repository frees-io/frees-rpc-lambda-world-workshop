package com.fortyseven.protocol

import freestyle.rpc.protocol._
import fs2.Stream

@service(Protobuf) trait SmartHomeService[F[_]] {

  def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse]

  def getTemperature(empty: Empty.type): Stream[F, Temperature]

  def comingBackMode(request: Stream[F, Location]): Stream[F, ComingBackModeResponse]
}
