package com.fortyseven.client

import com.fortyseven.protocol.{Temperature, TemperatureUnit}

import scala.math.BigDecimal.RoundingMode

case class TemperaturesSummary(
    temperatures: Vector[Temperature],
    averageTemperature: Temperature
) {
  def append(newTemperature: Temperature): TemperaturesSummary = {
    val temp = this.temperatures :+ newTemperature
    this.copy(
      temp,
      Temperature(
        BigDecimal(
          this.averageTemperature.value + (newTemperature.value - this.averageTemperature.value) / (this.temperatures.length + 1))
          .setScale(2, RoundingMode.HALF_UP)
          .toDouble,
        this.averageTemperature.unit
      )
    )
  }
}

object TemperaturesSummary {
  val empty: TemperaturesSummary =
    TemperaturesSummary(Vector.empty, Temperature(0d, TemperatureUnit("Fahrenheit")))
}
