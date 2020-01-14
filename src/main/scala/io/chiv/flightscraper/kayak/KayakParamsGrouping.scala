package io.chiv.flightscraper.kayak

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.AirlineCode
import org.http4s.Uri

case class KayakParamsGrouping(value: NonEmptyList[KayakParams]) {
  def toUri(airlineFilter: Option[NonEmptyList[AirlineCode]]): Uri = {
    val encodedParams = value.map(p => "/" + p.toUriParams).toList.mkString
    Uri.unsafeFromString(
      s"https://www.kayak.co.uk/flights$encodedParams?sort=price_a${airlineFilter
        .fold("")(ac => s"&fs=airlines=${ac.toList.map(_.value).mkString(",")}")}"
    )
  }
}
