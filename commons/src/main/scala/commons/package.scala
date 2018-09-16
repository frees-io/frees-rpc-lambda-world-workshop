package com.fortyseven

import cats.effect.IO
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

package object commons {
  implicit val L: Logger[IO] = Slf4jLogger.unsafeCreate[IO]
}
