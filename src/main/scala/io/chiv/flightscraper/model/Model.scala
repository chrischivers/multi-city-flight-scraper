package io.chiv.flightscraper.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

object Model {

  sealed trait Leg {
    def order: Int
    def from: AirportCode
    def to: AirportCode
  }
  case class FirstLeg(order: Int, from: AirportCode, to: AirportCode) extends Leg

  case class AdditionalLeg(order: Int, from: AirportCode, to: AirportCode, minimumDaysAfterPreviousLeg: Int, maximumDaysAfterPreviousLeg: Int)
      extends Leg

  object Leg {
    implicit val decoder: Decoder[Leg] = Decoder.instance { cursor =>
      cursor.downField("order").as[Int].flatMap { order =>
        if (order == 1) deriveDecoder[FirstLeg].apply(cursor)
        else deriveDecoder[AdditionalLeg].apply(cursor)
      }
    }
  }

  case class AirportCode(value: String)
  object AirportCode {
    implicit val decoder: Decoder[AirportCode] =
      Decoder.decodeString.map(AirportCode(_))
  }
  case class AirlineCode(value: String)
  object AirlineCode {
    implicit val decoder: Decoder[AirlineCode] =
      Decoder.decodeString.map(AirlineCode(_))
  }

}
