package io.chiv.flightscraper.db

import cats.data.NonEmptyList
import cats.effect.IO
import io.chiv.flightscraper.db.DB.RecordId
import io.chiv.flightscraper.kayak
import io.chiv.flightscraper.kayak.KayakParamsGrouping
import io.chiv.flightscraper.model.Model.Price
import io.chiv.flightscraper.model.Search

import scala.concurrent.duration._

trait DB {

  def withLock[T](f: IO[T]): IO[T]

  def nextParamsToProcess(inProgressTimeout: Duration = 30.minutes): IO[Option[KayakParamsGrouping.WithRecordId]]
  def completedRecords: IO[List[(KayakParamsGrouping.WithRecordId, Option[Price])]]

  def updatePrice(recordId: RecordId, lowestPrice: Option[Int]): IO[Unit]
  def setSearchData(data: Map[Search.Id, NonEmptyList[kayak.KayakParamsGrouping.WithoutRecordId]]): IO[Unit]

  def wipeData: IO[Unit]

}

object DB {
  case class RecordId(value: String)

  trait RecordStatus {
    def value: String
  }

  object RecordStatus {
    case object Open extends RecordStatus {
      override def value: String = "OPEN"
    }

    case object Completed extends RecordStatus {
      override def value: String = "COMPLETED"
    }

    case object InProgress extends RecordStatus {
      override def value: String = "IN_PROGRESS"
    }
  }

}
