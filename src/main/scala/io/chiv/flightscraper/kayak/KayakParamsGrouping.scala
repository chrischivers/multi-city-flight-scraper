package io.chiv.flightscraper.kayak

import cats.data.NonEmptyList
import org.http4s.Uri

case class KayakParamsGrouping(value: NonEmptyList[KayakParams]) {
  def toUri: Uri = {
    val encodedParams = value.map(p => "/" + p.toUriParams).toList.mkString
    Uri.unsafeFromString(
      s"https://www.kayak.co.uk/flights$encodedParams?sort=price_a"
    )
  }
}
