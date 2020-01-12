package io.chiv.flightscraper

import cats.effect.IO
import io.chiv.flightscraper.kayak.{KayakClient, KayakParams, KayakParamsGrouping}
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.util.TestGenerators
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{FunSuite, Matchers, WordSpec}

class FlightSearcherTest extends WordSpec with Matchers with TypeCheckedTripleEquals with TestGenerators {

  "flight searcher" should {
    "find lowest prices" should {
      "for a single search and single params grouping" in {
//        val paramsGrouping  = kayakParamsGrouping()
//        val search          = Search()
//        val flightSerarcher = setup(Map(paramsGrouping -> Some(100)))
//        flightSerarcher.process(sear)
      }

    }
  }

//  def setup(prices: Map[KayakParamsGrouping, Option[Int]] = Map.empty): FlightSearcher = {
//    val client = new KayakClient {
//      override def getLowestPrice(paramsGrouping: KayakParamsGrouping): IO[Option[Int]] = IO(prices.getOrElse(paramsGrouping, None))
//    }
//    FlightSearcher(client)
//  }

}
