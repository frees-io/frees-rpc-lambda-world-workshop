package com.fortyseven.client

import cats.effect._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.fortyseven.protocol._
import io.chrisdavenport.log4cats.Logger
import io.grpc.{CallOptions, ManagedChannel}
import monix.execution.Scheduler

import scala.concurrent.duration._

trait SmartHomeServiceApi[F[_]] {

  def isEmpty: F[Boolean]
}

object SmartHomeServiceApi {

  def apply[F[_]: Effect](clientRPCF: F[SmartHomeService.Client[F]])(
      implicit L: Logger[F]
  ): SmartHomeServiceApi[F] = new SmartHomeServiceApi[F] {
    override def isEmpty: F[Boolean] =
      for {
        clientRPC <- clientRPCF
        result    <- clientRPC.isEmpty(IsEmptyRequest())
        _         <- L.info(s"Result: $result")
      } yield result.result
  }

  def createInstance[F[_]: Effect](
      hostname: String,
      port: Int,
      sslEnabled: Boolean = false,
      tryToRemoveUnusedEvery: FiniteDuration = 30.minutes,
      removeUnusedAfter: FiniteDuration = 1.hour)(
      implicit L: Logger[F],
      TM: Timer[F],
      S: Scheduler): fs2.Stream[F, SmartHomeServiceApi[F]] = {

    def fromChannel(channel: ManagedChannel): SmartHomeService.Client[F] =
      SmartHomeService.clientFromChannel(channel, CallOptions.DEFAULT)

    ClientRPC
      .clientCache(
        (hostname, port).pure[F],
        sslEnabled,
        tryToRemoveUnusedEvery,
        removeUnusedAfter,
        fromChannel)
      .map(cache => SmartHomeServiceApi(cache.getClient))
  }
}
