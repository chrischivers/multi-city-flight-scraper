package io.chiv.flightscraper.config

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

case class Config(geckoDriverLocation: String, emailAccessKey: String, emailSecretKey: String, emailAddress: String)

object Config {
  def load() = IO {
    val config = ConfigFactory.load()
    Config(
      config.as[String]("geckoDriverLocation"),
      config.as[String]("emailAccessKey"),
      config.as[String]("emailSecretKey"),
      config.as[String]("emailAddress")
    )
  }
}
