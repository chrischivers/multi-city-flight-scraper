package io.chiv.flightscraper

import cats.data.NonEmptyList
import cats.effect.IO
import io.chiv.flightscraper.kayak.{KayakClient, KayakParams, KayakParamsGrouping}
import io.chiv.flightscraper.model.Search
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.instances.list._
import io.chiv.flightscraper.emailer.EmailClient
import io.chrisdavenport.log4cats.Logger

trait FlightSearcher {
  def process(searches: List[Search]): IO[Unit]
}

object FlightSearcher {
  def apply(kayakClient: KayakClient, emailClient: EmailClient)(implicit logger: Logger[IO]): FlightSearcher = new FlightSearcher {

    private def handleSearchError(search: Search, kayakParamsGrouping: KayakParamsGrouping): Throwable => IO[Option[Int]] = { err =>
      logger
        .error(s"Error for search $search for params combination $kayakParamsGrouping. Error [$err]") >>
        IO.pure(None)
    }

    override def process(searches: List[Search]): IO[Unit] =
      searches.zipWithIndex.traverse {
        case (search, i) =>
          for {
            _                 <- logger.info(s"Processing search $search (${i + 1}/${searches.size}")
            paramCombinations = KayakParams.paramCombinationsFrom(search)
            _                 <- logger.info(s"${paramCombinations.size} parameter combinations to process")
            initialResults <- paramCombinations.zipWithIndex.traverse {
                               case (paramCombination, i) =>
                                 for {
                                   _ <- logger.info(s"Processing parameter combination ${i + 1}/${paramCombinations.size}")
                                   lowestPrice <- kayakClient
                                                   .getLowestPrice(paramCombination, search.airlineFilter, search.layoverFilter)
                                                   .handleErrorWith(handleSearchError(search, paramCombination))
                                                   .map((paramCombination, _))
                                 } yield (paramCombination, lowestPrice)

                             }
            _ <- logger.info(s"Retrying results where price was not found")
            confirmedResults <- initialResults.traverse {
                                 case (paramCombination, (_, None)) =>
                                   kayakClient
                                     .getLowestPrice(paramCombination, search.airlineFilter, search.layoverFilter)
                                     .handleErrorWith(handleSearchError(search, paramCombination))
                                     .map((paramCombination, _))
                                 case (_, x) => IO.pure(x)
                               }
            lowestPrices = confirmedResults
              .collect { case (params, Some(price)) => (price, params) }
              .sortBy { case (price, _) => price }
              .take(10)
            _ <- NonEmptyList.fromList(lowestPrices).fold(IO.unit)(emailClient.sendNotification(search, _))
          } yield ()
      }.void

  }
}
