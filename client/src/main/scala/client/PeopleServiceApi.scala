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

trait PeopleServiceApi[F[_]] {

  def getPersonByName(name: String): F[Person]

}

object PeopleServiceApi {

  def apply[F[_]: Effect](clientRPCF: F[PeopleService.Client[F]])(
      implicit L: Logger[F]
  ): PeopleServiceApi[F] = new PeopleServiceApi[F] {
    override def getPersonByName(name: String): F[Person] =
      for {
        clientRPC <- clientRPCF
        _         <- L.info(s"Request: $name")
        result    <- clientRPC.getPerson(PeopleRequest(name))
        _         <- L.info(s"Result: $result")
      } yield result.person
  }

  def createInstance[F[_]: Effect](
      hostname: String,
      port: Int,
      sslEnabled: Boolean = false,
      tryToRemoveUnusedEvery: FiniteDuration = 30.minutes,
      removeUnusedAfter: FiniteDuration = 1.hour)(
      implicit L: Logger[F],
      TM: Timer[F],
      S: Scheduler): fs2.Stream[F, PeopleServiceApi[F]] = {

    def fromChannel(channel: ManagedChannel): PeopleService.Client[F] =
      PeopleService.clientFromChannel(channel, CallOptions.DEFAULT)

    ClientRPC
      .clientCache(
        (hostname, port).pure[F],
        sslEnabled,
        tryToRemoveUnusedEvery,
        removeUnusedAfter,
        fromChannel)
      .map(cache => PeopleServiceApi(cache.getClient))
  }
}
