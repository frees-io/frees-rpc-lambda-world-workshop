package com.fortyseven.commons

import cats.{Show, Traverse}
import cats.effect.Sync
import cats.instances.list._
import cats.syntax.show._
import fs2.Sink
import io.chrisdavenport.log4cats.Logger

class LogSink[F[_]: Sync: Logger] {
  def showLine[I: Show]: Sink[F, I] = Sink[F, I](item => Logger[F].info(item.show))

  def showLines[I: Show]: Sink[F, List[I]] = Sink[F, List[I]] { items =>
    Traverse[List].traverse_(items)(item => Logger[F].info(item.show))
  }
}

object LogSink {
  implicit def instance[F[_]: Sync: Logger]: LogSink[F] = new LogSink[F]

  def apply[F[_]](implicit ev: LogSink[F]): LogSink[F] = ev
}
