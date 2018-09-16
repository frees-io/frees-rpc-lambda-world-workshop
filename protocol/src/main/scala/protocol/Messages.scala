package com.fortyseven.protocol

import freestyle.rpc.protocol.message

case class TemperatureUnit(value: String) extends AnyVal

case class Temperature(value: Double, unit: TemperatureUnit)

case class Point(lat: Double, long: Double)

case class Location(currentLocation: Point, destination: Point, distanceToDestination: Double)

case class SmartHomeAction(value: String) extends AnyVal

@message
final case class IsEmptyRequest()

@message
final case class IsEmptyResponse(result: Boolean)
