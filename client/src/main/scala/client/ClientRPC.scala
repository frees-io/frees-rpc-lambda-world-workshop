package com.fortyseven.client

import java.net.InetAddress

import cats.effect.{Effect, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import freestyle.rpc.ChannelForAddress
import freestyle.rpc.client.cache.ClientCache
import freestyle.rpc.client.cache.ClientCache.HostPort
import freestyle.rpc.client.{ManagedChannelInterpreter, UsePlaintext}
import io.grpc.ManagedChannel
import monix.execution.Scheduler

import scala.concurrent.duration.FiniteDuration

object ClientRPC {

  def clientCache[F[_]: Effect, Client[_[_]]](
      hostPort: F[HostPort],
      sslEnabled: Boolean,
      tryToRemoveUnusedEvery: FiniteDuration,
      removeUnusedAfter: FiniteDuration,
      fromChannel: ManagedChannel => Client[F])(
      implicit TM: Timer[F],
      S: Scheduler): fs2.Stream[F, ClientCache[Client, F]] = {

    def serviceClient(hostname: String, port: Int): F[(Client[F], F[Unit])] =
      for {
        ip <- Effect[F].delay(InetAddress.getByName(hostname).getHostAddress)
        channelFor    = ChannelForAddress(ip, port)
        channelConfig = if (!sslEnabled) List(UsePlaintext()) else Nil
        channel <- Effect[F].delay(
          new ManagedChannelInterpreter[F](channelFor, channelConfig)
            .build(channelFor, channelConfig)
        )
      } yield (fromChannel(channel), Effect[F].delay(channel.shutdown).void)

    ClientCache
      .impl[Client, F](
        hostPort,
        Function.tupled(serviceClient),
        tryToRemoveUnusedEvery,
        removeUnusedAfter
      )
  }
}
