package io.chiv.flightscraper.kayak

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit._

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.{AdditionalLeg, AirportCode, FirstLeg, Leg}
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.util._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

case class KayakParams(order: Int, from: AirportCode, to: AirportCode, date: LocalDate) {
  def toUriParams =
    s"${from.value}-${to.value}/${KayakParams.urlEncodedDateFor(date)}"

}

object KayakParams {

  implicit val encoder: Encoder[KayakParams] = deriveEncoder

  implicit val decoder: Decoder[KayakParams] = deriveDecoder

  def urlEncodedDateFor(localDate: LocalDate) = {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    s"${localDate.format(dateTimeFormatter)}-h"
  }

  def paramCombinationsFrom(search: Search): List[KayakParamsGrouping.WithoutRecordId] = {

    def helper(accumulatedParams: List[KayakParams], previousLegDate: LocalDate, remainingLegs: List[Leg]): List[KayakParams] =
      remainingLegs match {
        case Nil => accumulatedParams
        case FirstLeg(order, from, to) :: tail =>
          helper(
            accumulatedParams :+ KayakParams(order, from, to, previousLegDate),
            previousLegDate,
            tail
          )
        case AdditionalLeg(order, from, to, minDaysAfter, maxDaysAfter) :: tail =>
          datesBetween(
            previousLegDate.plusDays(minDaysAfter),
            previousLegDate.plusDays(maxDaysAfter)
          ).flatMap { date =>
            helper(
              accumulatedParams :+ KayakParams(order, from, to, date),
              date,
              tail
            )
          }
      }

    val possibleStartDates =
      datesBetween(search.earliestDepartureDate, search.latestDepartureDate)

    def withinMinMaxTripLength(params: List[KayakParams]): Option[Boolean] =
      for {
        min      <- search.minimumTotalTripLength
        max      <- search.maximumTotalTripLength
        firstLeg <- params.headOption
        lastLeg  <- params.lastOption
      } yield {
        val tripLength = DAYS.between(firstLeg.date, lastLeg.date)
        tripLength >= min && tripLength <= max
      }

    def returnWithinEarliestLatestDates(params: List[KayakParams]): Option[Boolean] =
      for {
        earliest <- search.earliestReturnFlightDate
        latest   <- search.latestReturnFlightDate
        lastLeg  <- params.lastOption
      } yield {
        (lastLeg.date.isAfter(earliest) || lastLeg.date.isEqual(earliest)) && (lastLeg.date.isBefore(latest) || lastLeg.date.isEqual(latest))
      }

    possibleStartDates
      .flatMap { startDate =>
        helper(List.empty, startDate, search.legs.toList)
      }
      .grouped(search.legs.size)
      .toList
      .distinct
      .filter(params => withinMinMaxTripLength(params).getOrElse(true))
      .filter(params => returnWithinEarliestLatestDates(params).getOrElse(true))
      .map(NonEmptyList.fromList)
      .collect {
        case Some(l) => KayakParamsGrouping.WithoutRecordId(search.id, l)
      }
  }

}
