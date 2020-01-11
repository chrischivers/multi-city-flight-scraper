package io.chiv.flightscraper.kayak

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.{
  AdditionalLeg,
  AirportCode,
  FirstLeg,
  Leg
}
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.util._

case class KayakParams(order: Int,
                       from: AirportCode,
                       to: AirportCode,
                       date: LocalDate) {
  def toUriParams =
    s"${from.value}-${to.value}/${KayakParams.urlEncodedDateFor(date)}"
}

object KayakParams {

  def urlEncodedDateFor(localDate: LocalDate) = {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    s"${localDate.format(dateTimeFormatter)}-h"
  }

  def paramCombinationsFrom(search: Search): List[KayakParamsGrouping] = {

    def helper(accumulatedParams: List[KayakParams],
               previousLegDate: LocalDate,
               remainingLegs: List[Leg]): List[KayakParams] = {
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

    }

    val possibleStartDates =
      datesBetween(search.earliestDepartureDate, search.latestDepartureDate)

    possibleStartDates
      .flatMap { startDate =>
        helper(List.empty, startDate, search.legs.toList)
      }
      .grouped(search.legs.size)
      .toList
      .distinct
      .map(NonEmptyList.fromList)
      .collect {
        case Some(l) => KayakParamsGrouping(l)
      }
  }

}
