package io.chiv.flightscraper.kayak

import java.time.LocalDate

import cats.data.NonEmptyList
import io.chiv.flightscraper.model.Model.{AdditionalLeg, AirportCode, FirstLeg}
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.util.TestGenerators
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{Matchers, WordSpec}

class KayakParamsTest extends WordSpec with Matchers with TypeCheckedTripleEquals with TestGenerators {

  val airportCode1 = airportCode()
  val airportCode2 = airportCode()
  val airportCode3 = airportCode()
  val airportCode4 = airportCode()

  "paramCombinationsFrom" should {
    "generate all combinations of parameters for a given search" should {
      "where there is one leg and multiple start dates" in {

        //TODO use scalacheck
        val earliestDeparture   = localDate()
        val daysToAdd           = int()
        val latestDepartureDate = earliestDeparture.plusDays(daysToAdd)

        val leg = FirstLeg(1, airportCode1, airportCode2)
        val search = Search(
          NonEmptyList.of(leg),
          None,
          earliestDeparture,
          latestDepartureDate,
          None,
          None,
          None,
          None
        )
        val result = KayakParams.paramCombinationsFrom(search)
        result.size should ===(daysToAdd + 1)
        val expectedResult = (0 to daysToAdd).toList.map { daysToAdd =>
          KayakParamsGrouping(
            NonEmptyList.of(
              KayakParams(
                1,
                airportCode1,
                airportCode2,
                earliestDeparture.plusDays(daysToAdd)
              )
            )
          )
        }
        result should ===(expectedResult)
      }

      "where there are two legs and a single start date" in {

        val earliestDeparture   = localDate()
        val latestDepartureDate = earliestDeparture

        val leg1 = FirstLeg(1, airportCode1, airportCode2)

        val minimumDaysAfterPreviousLeg = int()
        val maximumDaysAfterPreviousLeg = minimumDaysAfterPreviousLeg + int()

        val leg2 = AdditionalLeg(
          2,
          airportCode2,
          airportCode3,
          minimumDaysAfterPreviousLeg,
          maximumDaysAfterPreviousLeg
        )
        val search = Search(
          NonEmptyList.of(leg1, leg2),
          None,
          earliestDeparture,
          latestDepartureDate,
          None,
          None,
          None,
          None
        )
        val result = KayakParams.paramCombinationsFrom(search)
        result.size should ===(
          (minimumDaysAfterPreviousLeg to maximumDaysAfterPreviousLeg).size
        )

        val expectedResult =
          (minimumDaysAfterPreviousLeg to maximumDaysAfterPreviousLeg).toList.map { daysToAddAfterPreviousLeg =>
            KayakParamsGrouping(
              NonEmptyList.of(
                KayakParams(1, leg1.from, leg1.to, earliestDeparture),
                KayakParams(
                  2,
                  leg2.from,
                  leg2.to,
                  earliestDeparture.plusDays(daysToAddAfterPreviousLeg)
                )
              )
            )
          }
        result should ===(expectedResult)
      }

      "where there are two legs and multiple start dates" in {

        val earliestDeparture   = localDate()
        val daysToAdd           = int()
        val latestDepartureDate = earliestDeparture.plusDays(daysToAdd)

        val leg1 = FirstLeg(1, airportCode1, airportCode2)

        val minimumDaysAfterPreviousLeg = int()
        val maximumDaysAfterPreviousLeg = minimumDaysAfterPreviousLeg + int()

        val leg2 = AdditionalLeg(
          2,
          airportCode2,
          airportCode3,
          minimumDaysAfterPreviousLeg,
          maximumDaysAfterPreviousLeg
        )
        val search = Search(
          NonEmptyList.of(leg1, leg2),
          None,
          earliestDeparture,
          latestDepartureDate,
          None,
          None,
          None,
          None
        )
        val result = KayakParams.paramCombinationsFrom(search)
        result.size should ===(
          (0 to daysToAdd).size * (minimumDaysAfterPreviousLeg to maximumDaysAfterPreviousLeg).size
        )

        val expectedResult = (0 to daysToAdd).toList.flatMap { departureDateOffset =>
          val departureDate = earliestDeparture.plusDays(departureDateOffset)
          (minimumDaysAfterPreviousLeg to maximumDaysAfterPreviousLeg).toList.map { daysToAddAfterPreviousLeg =>
            KayakParamsGrouping(
              NonEmptyList.of(
                KayakParams(1, leg1.from, leg1.to, departureDate),
                KayakParams(
                  2,
                  leg2.from,
                  leg2.to,
                  departureDate.plusDays(daysToAddAfterPreviousLeg)
                )
              )
            )
          }
        }
        result should ===(expectedResult)

      }
      "where there are three legs and multiple start dates" in {

        //TODO use scalacheck
        val earliestDeparture   = localDate()
        val daysToAdd           = int()
        val latestDepartureDate = earliestDeparture.plusDays(daysToAdd)

        val leg1 = FirstLeg(1, airportCode1, airportCode2)

        val leg2MinimumDaysAfterPreviousLeg = int()
        val leg2MaximumDaysAfterPreviousLeg = leg2MinimumDaysAfterPreviousLeg + int()
        val leg2 = AdditionalLeg(
          2,
          airportCode2,
          airportCode3,
          leg2MinimumDaysAfterPreviousLeg,
          leg2MaximumDaysAfterPreviousLeg
        )

        val leg3MinimumDaysAfterPreviousLeg = int()
        val leg3MaximumDaysAfterPreviousLeg = leg3MinimumDaysAfterPreviousLeg + int()

        val leg3 = AdditionalLeg(
          3,
          airportCode3,
          airportCode4,
          leg3MinimumDaysAfterPreviousLeg,
          leg3MaximumDaysAfterPreviousLeg
        )
        val search = Search(
          NonEmptyList.of(leg1, leg2, leg3),
          None,
          earliestDeparture,
          latestDepartureDate,
          None,
          None,
          None,
          None
        )
        val result = KayakParams.paramCombinationsFrom(search)
        result.size should ===(
          (0 to daysToAdd).size * (leg2MinimumDaysAfterPreviousLeg to leg2MaximumDaysAfterPreviousLeg).size * (leg3MinimumDaysAfterPreviousLeg to leg3MaximumDaysAfterPreviousLeg).size
        )

        val expectedResult = (0 to daysToAdd).toList.flatMap { departureDateOffset =>
          val departureDateLeg1 = earliestDeparture.plusDays(departureDateOffset)
          (leg2MinimumDaysAfterPreviousLeg to leg2MaximumDaysAfterPreviousLeg).toList.flatMap { leg2DaysToAdd =>
            val departureDateLeg2 = departureDateLeg1.plusDays(leg2DaysToAdd)
            (leg3MinimumDaysAfterPreviousLeg to leg3MaximumDaysAfterPreviousLeg).toList.map { leg3DaysToAdd =>
              KayakParamsGrouping(
                NonEmptyList.of(
                  KayakParams(1, leg1.from, leg1.to, departureDateLeg1),
                  KayakParams(
                    2,
                    leg2.from,
                    leg2.to,
                    departureDateLeg1.plusDays(leg2DaysToAdd)
                  ),
                  KayakParams(
                    3,
                    leg3.from,
                    leg3.to,
                    departureDateLeg2.plusDays(leg3DaysToAdd)
                  )
                )
              )
            }
          }
        }
        result should ===(expectedResult)

      }

      "filters out dates that are outside of the minimum/maximum trip duration" in {

        val earliestDeparture   = LocalDate.now()
        val latestDepartureDate = earliestDeparture

        val leg1 = FirstLeg(1, airportCode1, airportCode2)

        val minimumDaysAfterPreviousLeg = 5
        val maximumDaysAfterPreviousLeg = 10

        val leg2 = AdditionalLeg(
          2,
          airportCode2,
          airportCode3,
          minimumDaysAfterPreviousLeg,
          maximumDaysAfterPreviousLeg
        )

        val minimumOverallTripLength = 7
        val maximumOverallTripLength = 8

        val search = Search(
          NonEmptyList.of(leg1, leg2),
          None,
          earliestDeparture,
          latestDepartureDate,
          None,
          None,
          Some(minimumOverallTripLength),
          Some(maximumOverallTripLength)
        )
        val result = KayakParams.paramCombinationsFrom(search)
        result.size should ===(
          (minimumDaysAfterPreviousLeg to maximumDaysAfterPreviousLeg).count(i => (minimumOverallTripLength to maximumOverallTripLength).contains(i))
        )

        val expectedResult =
          (minimumDaysAfterPreviousLeg to maximumDaysAfterPreviousLeg)
            .filter(i => (minimumOverallTripLength to maximumOverallTripLength).contains(i))
            .toList
            .map { daysToAddAfterPreviousLeg =>
              KayakParamsGrouping(
                NonEmptyList.of(
                  KayakParams(1, leg1.from, leg1.to, earliestDeparture),
                  KayakParams(
                    2,
                    leg2.from,
                    leg2.to,
                    earliestDeparture.plusDays(daysToAddAfterPreviousLeg)
                  )
                )
              )
            }
        result should ===(expectedResult)
      }

      "filters out trips that are outside of the earliest/latest return dates" in {

        val earliestDeparture   = LocalDate.now()
        val latestDepartureDate = earliestDeparture

        val leg1 = FirstLeg(1, airportCode1, airportCode2)

        val minimumDaysAfterPreviousLeg = 5
        val maximumDaysAfterPreviousLeg = 10

        val leg2 = AdditionalLeg(
          2,
          airportCode2,
          airportCode3,
          minimumDaysAfterPreviousLeg,
          maximumDaysAfterPreviousLeg
        )

        val earliestReturnDate = earliestDeparture.plusDays(7)
        val latestReturnDate   = latestDepartureDate.plusDays(9)

        val search = Search(
          NonEmptyList.of(leg1, leg2),
          None,
          earliestDeparture,
          latestDepartureDate,
          Some(earliestReturnDate),
          Some(latestReturnDate),
          None,
          None
        )
        val result = KayakParams.paramCombinationsFrom(search)
        result.size should ===(3)

      }
    }

  }
}
