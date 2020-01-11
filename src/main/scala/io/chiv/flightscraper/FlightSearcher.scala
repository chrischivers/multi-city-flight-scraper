package io.chiv.flightscraper

import cats.effect.IO
import io.chiv.flightscraper.Main.logger
import io.chiv.flightscraper.kayak.{KayakClient, KayakParams, KayakParamsGrouping}
import io.chiv.flightscraper.model.Search
import cats.syntax.traverse._
import cats.syntax.flatMap._
import cats.instances.list._

trait FlightSearcher {
  def process(searches: List[Search]): IO[Map[Search, (KayakParamsGrouping, Int)]]
}

object FlightSearcher {
  def apply(kayakClient: KayakClient): FlightSearcher = new FlightSearcher {

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
                             lowestPrice <- kayakClient.getLowestPrice(paramCombination).map((search, paramCombination, _)).handleErrorWith { err =>
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

//case class S3Obj(value: String)
//
//case class Source(bucketName: String, paths: List[String])
//
//trait S3Client {
//  def list(bucketName: String, path: String): IO[List[S3Obj]]
//  def delete(file: S3Obj): IO[Unit]
//}
//
//object x {
//  val x = new Processor(???).run(???)()
//}
//
//class Processor(s3Client: S3Client) {
//  def run(source: Source)(predicate: S3Obj => Boolean) =
//    for {
//      files         <- source.paths.flatTraverse(x => s3Client.list(source.bucketName, x))
//      filesToDelete = files.filter(f => predicate(f))
//      _             <- filesToDelete.traverse(x => s3Client.delete(x))
//    } yield ()
//}
