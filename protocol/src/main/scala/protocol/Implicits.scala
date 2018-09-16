package com.fortyseven.protocol

import cats.Show

object implicits {
  implicit val catsShowInstanceForSmartHomeAction: Show[SmartHomeAction] =
    new Show[SmartHomeAction] {
      override def show(action: SmartHomeAction): String = s"${action.value}\n"
    }
}
