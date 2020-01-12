package io.chiv.flightscraper

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import io.chiv.flightscraper.kayak.KayakClient
import io.chiv.flightscraper.selenium.WebDriver
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

object Main extends IOApp {

  implicit val logger: SelfAwareStructuredLogger[IO] =
    Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {

    val app = for {
      searches <- SearchConfig
                   .load()
      webDriver = WebDriver.resource(
//        "/Users/chrichiv/Downloads/geckodriver",
        "/usr/bin/geckodriver",
        headless = true
      )
      kayakClient  = KayakClient.apply(webDriver)
      processor    = FlightSearcher(kayakClient)
      lowestPrices <- processor.process(searches)
      _            = println(lowestPrices)

    } yield ()

    app.map(_ => ExitCode.Success)

  }
}
