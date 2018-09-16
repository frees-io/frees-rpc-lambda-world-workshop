package com.fortyseven.protocol

import freestyle.rpc.protocol.message

case class TemperatureUnit(value: String) extends AnyVal

case class Temperature(value: Double, unit: TemperatureUnit)

@message
final case class IsEmptyRequest()

@message
final case class IsEmptyResponse(result: Boolean)
