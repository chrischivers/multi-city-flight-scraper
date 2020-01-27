package io.chiv.flightscraper.kayak

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.{AirlineCode, AirportCode}

case class KayakParamsGrouping(value: NonEmptyList[KayakParams]) {
  def toUri(airlineFilter: Option[NonEmptyList[AirlineCode]], layoverFilter: Option[AirportCode]): String = {
    val encodedParams = value.map(p => "/" + p.toUriParams).toList.mkString
    s"https://www.kayak.co.uk/flights$encodedParams?sort=price_a&fs=${airlineFilter
      .fold("")(ac => s"airlines=${ac.toList.map(_.value).mkString(",")};")}${layoverFilter.fold("")(ac => s"layoverair=~${ac.value}")}"
  }

  override def toString: String =
    value.toList
      .sortBy(_.order)
      .map { params =>
        s"""
        |
        |Leg: ${params.order}
        |From: ${params.from.value}
        |To: ${params.to.value}
        |Date: ${params.date.toString}
        |
        |""".stripMargin
        params.date
      }
      .mkString("\n")
}
