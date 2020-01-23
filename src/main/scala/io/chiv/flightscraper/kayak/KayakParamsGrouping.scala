package io.chiv.flightscraper.kayak

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.AirlineCode

case class KayakParamsGrouping(value: NonEmptyList[KayakParams]) {
  def toUri(airlineFilter: Option[NonEmptyList[AirlineCode]]): String = {
    val encodedParams = value.map(p => "/" + p.toUriParams).toList.mkString
    s"https://www.kayak.co.uk/flights$encodedParams?sort=price_a&fs=${airlineFilter
      .fold("")(ac => s"airlines=${ac.toList.map(_.value).mkString(",")};")}layoverair=~CAN" //todo make this a parameter

  }
}
