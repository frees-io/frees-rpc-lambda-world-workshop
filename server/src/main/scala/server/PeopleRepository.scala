package com.fortyseven.server

import cats.data.StateT
import cats.effect.Sync
import cats.syntax.applicative._
import com.fortyseven.protocol.Person

trait PeopleRepository[F[_]] {
  def getPerson(name: String): F[Person]
}

object PeopleRepository {
  implicit def instance[F[_]: Sync]: PeopleRepository[F] = new PeopleRepository[F] {

    case class Seed(long: Long) {
      def next: Seed = Seed(long * 6364136223846793005L + 1442695040888963407L)
    }

    private[this] def initialSeed(name: String) = Seed(name.map(_.toLong).sum)

    private[this] val nextAge: StateT[F, Seed, Int] = StateT(
      seed => (seed.next, (seed.long % 100).toInt).pure[F])

    override def getPerson(name: String): F[Person] =
      nextAge.map(age => Person(name, age)).runA(initialSeed(name))
  }

  def apply[F[_]](implicit ev: PeopleRepository[F]): PeopleRepository[F] = ev
}
