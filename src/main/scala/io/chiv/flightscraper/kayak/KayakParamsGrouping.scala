package io.chiv.flightscraper.kayak

import java.util.UUID

import cats.data.NonEmptyList
import io.chiv.flightscraper.db.DB.RecordId
import io.chiv.flightscraper.model.Model.{AirlineCode, AirportCode}
import io.chiv.flightscraper.model.Search

sealed trait KayakParamsGrouping {
  def toUri(airlineFilter: Option[NonEmptyList[AirlineCode]], layoverFilter: Option[AirportCode]): String
}

object KayakParamsGrouping {

  case class WithRecordId(searchId: Search.Id, params: NonEmptyList[KayakParams], recordId: RecordId) extends KayakParamsGrouping {
    override def toUri(airlineFilter: Option[NonEmptyList[AirlineCode]], layoverFilter: Option[AirportCode]): String =
      KayakParamsGrouping.toUri(params, airlineFilter, layoverFilter)
  }

  case class WithoutRecordId(searchId: Search.Id, params: NonEmptyList[KayakParams]) extends KayakParamsGrouping {
    override def toUri(airlineFilter: Option[NonEmptyList[AirlineCode]], layoverFilter: Option[AirportCode]): String =
      KayakParamsGrouping.toUri(params, airlineFilter, layoverFilter)
  }

  def toUri(params: NonEmptyList[KayakParams], airlineFilter: Option[NonEmptyList[AirlineCode]], layoverFilter: Option[AirportCode]): String = {
    val encodedParams = params.map(p => "/" + p.toUriParams).toList.mkString
    s"https://www.kayak.co.uk/flights$encodedParams?sort=price_a&fs=${airlineFilter
      .fold("")(ac => s"airlines=${ac.toList.map(_.value).mkString(",")};")}${layoverFilter.fold("")(ac => s"layoverair=~${ac.value}")}"
  }
}
