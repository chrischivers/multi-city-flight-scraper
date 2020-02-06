package io.chiv.flightscraper.util

import java.time.LocalDate

import cats.data.NonEmptyList
import io.chiv.flightscraper.kayak.{KayakParams, KayakParamsGrouping}
import io.chiv.flightscraper.model.Model.AirportCode
import io.chiv.flightscraper.model.Search
import org.scalacheck.{Arbitrary, Gen}

trait TestGenerators {
  private def intGen(maxInt: Int = 100) = Gen.choose(0, maxInt)
  private val airportCodeGen            = Gen.listOfN(3, Gen.alphaChar).map(l => AirportCode(l.mkString.toUpperCase))
  private val localDateGen              = Gen.choose(LocalDate.now().toEpochDay, LocalDate.now().plusYears(1).toEpochDay).map(LocalDate.ofEpochDay)

  def int(maxInt: Int = 100) = intGen(maxInt).sample.get
  def airportCode()          = airportCodeGen.sample.get
  def localDate()            = localDateGen.sample.get
  def kayakParams()          = KayakParams(int(), airportCode(), airportCode(), localDate())
  def kayakParamsGrouping() = {
    val startDate = localDate()
    val params = (0 to int() + 1).map { i =>
      kayakParams().copy(order = i, date = startDate.plusDays(int(5) * i))
    }.toList
    KayakParamsGrouping.WithoutRecordId(Search.Id("1"), NonEmptyList.fromListUnsafe(params))
  }
}
