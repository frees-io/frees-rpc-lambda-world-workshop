package com.fortyseven.protocol

import freestyle.rpc.protocol.{service, Protobuf}

@service(Protobuf) trait SmartHomeService[F[_]] {

  def isEmpty(request: IsEmptyRequest): F[IsEmptyResponse]

}
