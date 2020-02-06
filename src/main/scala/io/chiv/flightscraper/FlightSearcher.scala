package io.chiv.flightscraper

import cats.data.NonEmptyList
import cats.effect.IO
import io.chiv.flightscraper.kayak.{KayakClient, KayakParams, KayakParamsGrouping}
import io.chiv.flightscraper.model.Search
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.instances.list._
import cats.instances.map._
import io.chiv.flightscraper.db.DB
import io.chiv.flightscraper.emailer.EmailClient
import io.chiv.flightscraper.model.Model.Price
import io.chrisdavenport.log4cats.Logger
import util._

trait FlightSearcher {
  def processNext(): IO[Unit]
}

object FlightSearcher {
  def apply(kayakClient: KayakClient, emailClient: EmailClient, dbClient: DB, searches: List[Search])(
    implicit logger: Logger[IO]
  ): FlightSearcher = new FlightSearcher {

    private def handleSearchError(search: Search, kayakParamsGrouping: KayakParamsGrouping): Throwable => IO[Option[Int]] = { err =>
      logger
        .error(s"Error for search $search for params combination $kayakParamsGrouping. Error [$err]") >>
        IO.pure(None)
    }

    override def processNext(): IO[Unit] =
      dbClient.withLock {
        dbClient.nextParamsToProcess
          .flatMap {
            case None =>
              dbClient.completedRecords.flatMap {
                case Nil =>
                  logger.info("Table is empty. Setting up table with search parameters") >>
                    dbClient.setTable(paramsForSearches)
                case completedData =>
                  logger.info("Searches have been completed. Collecting lowest prices and emailing...") >>
                    collectLowestPricesAndEmail(completedData)
              }
            case Some(kpg) =>
              logger.info(s"Processing Kayak Parameter group ${kpg.recordId} for search id ${kpg.searchId}") >>
                process(kpg)
          }
      }

    private def collectLowestPricesAndEmail(data: List[(KayakParamsGrouping.WithRecordId, Option[Price])]): IO[Unit] =
      data
        .groupBy(_._1.searchId)
        .toList
        .traverse {
          case (searchId, params) =>
            searches.find(_.id == searchId).fold[IO[Unit]](IO.raiseError(new RuntimeException(s"No search found for search Id $searchId"))) {
              search =>
                val lowestPrices = params
                  .collect {
                    case (r, Some(price)) => (price, r)
                  }
                  .sortBy { case (price, _) => price.value }
                  .take(10)
                NonEmptyList.fromList(lowestPrices).fold(IO.unit)(emailClient.sendNotification(search, _))
            }
        }
        .void

    private def paramsForSearches: Map[Search.Id, NonEmptyList[KayakParamsGrouping.WithoutRecordId]] =
      searches
        .map { search =>
          (search.id, KayakParams.paramCombinationsFrom(search))
        }
        .collect {
          case (searchId, head :: tail) => (searchId, NonEmptyList.of(head, tail: _*))
        }
        .toMap

    private def process(kayakParamsGrouping: KayakParamsGrouping.WithRecordId) =
      for {
        search <- searches
                   .find(_.id == kayakParamsGrouping.searchId)
                   .fold[IO[Search]](IO.raiseError(new RuntimeException(s"Unable to locate search for ID: ${kayakParamsGrouping.searchId}")))(IO.pure)
        result <- kayakClient
                   .getLowestPrice(kayakParamsGrouping, search.airlineFilter, search.layoverFilter)
                   .handleErrorWith(handleSearchError(search, kayakParamsGrouping))
                   .retryIf(3, _.isEmpty)

        _ <- dbClient.updatePrice(kayakParamsGrouping.recordId, result)
      } yield ()
  }

}
