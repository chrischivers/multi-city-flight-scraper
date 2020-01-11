package io.chiv.flightscraper.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.{AirlineCode, Leg}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Search(legs: NonEmptyList[Leg],
                  airlineFilter: Option[NonEmptyList[AirlineCode]],
                  earliestDepartureDate: LocalDate,
                  latestDepartureDate: LocalDate,
                  minimumTotalTripLength: Option[Int],
                  maximumTotalTripLength: Option[Int])

object Search {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  implicit val localDateDecoder: Decoder[LocalDate] =
    Decoder.decodeString.map(s => LocalDate.parse(s, dateTimeFormatter))
  implicit val decoder: Decoder[Search] = deriveDecoder
}
