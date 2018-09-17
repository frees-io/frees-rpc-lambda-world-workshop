package com.fortyseven.protocol

import freestyle.rpc.protocol.message

@message
final case class IsEmptyRequest()

@message
final case class IsEmptyResponse(result: Boolean)
