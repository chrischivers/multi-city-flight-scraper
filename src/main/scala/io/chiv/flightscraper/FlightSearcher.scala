package io.chiv.flightscraper

import cats.effect.IO
import io.chiv.flightscraper.kayak.{KayakClient, KayakParams, KayakParamsGrouping}
import io.chiv.flightscraper.model.Search
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.instances.list._
import io.chrisdavenport.log4cats.Logger

trait FlightSearcher {
  def process(searches: List[Search]): IO[Map[Search, (KayakParamsGrouping, Int)]]
}

object FlightSearcher {
  def apply(kayakClient: KayakClient)(implicit logger: Logger[IO]): FlightSearcher = new FlightSearcher {

    override def process(searches: List[Search]): IO[Map[Search, (KayakParamsGrouping, Int)]] =
      searches.zipWithIndex
        .traverse {
          case (search, i) =>
            for {
              _                 <- logger.info(s"Processing search $search (${i + 1}/${searches.size}")
              paramCombinations = KayakParams.paramCombinationsFrom(search)
              _                 <- logger.info(s"${paramCombinations.size} parameter combinations to process")
              result <- paramCombinations.zipWithIndex.traverse {
                         case (paramCombination, i) =>
                           for {
                             _ <- logger.info(s"Processing parameter combination ${i + 1}/${paramCombinations.size}")
                             lowestPrice <- kayakClient
                                             .getLowestPrice(paramCombination, search.airlineFilter)
                                             .map((search, paramCombination, _))
                                             .handleErrorWith { err =>
                                               logger
                                                 .error(s"Error for search $search for params combination $paramCombination. Error [$err]") >>
                                                 IO.raiseError(err)
                                             }
                           } yield lowestPrice

                       }
            } yield result
        }
        .map(_.flatten)
        .map {
          _.groupBy { case (search, _, _) => search }
            .mapValues {
              _.collect { case (_, params, Some(price)) => (params, price) }
                .minBy { case (_, price) => price }
            }
        }

  }
}
