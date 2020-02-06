package io.chiv.flightscraper.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.{AirlineCode, AirportCode, Leg}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class Search(id: Search.Id,
                  name: String,
                  legs: NonEmptyList[Leg],
                  airlineFilter: Option[NonEmptyList[AirlineCode]],
                  layoverFilter: Option[AirportCode],
                  earliestDepartureDate: LocalDate,
                  latestDepartureDate: LocalDate,
                  earliestReturnFlightDate: Option[LocalDate],
                  latestReturnFlightDate: Option[LocalDate],
                  minimumTotalTripLength: Option[Int],
                  maximumTotalTripLength: Option[Int])

object Search {

  case class Id(value: String)

  object Id {
    implicit val decoder: Decoder[Id] = Decoder.decodeString.map(Id.apply)
  }

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  implicit val localDateDecoder: Decoder[LocalDate] =
    Decoder.decodeString.map(s => LocalDate.parse(s, dateTimeFormatter))
  implicit val decoder: Decoder[Search] = deriveDecoder
}
