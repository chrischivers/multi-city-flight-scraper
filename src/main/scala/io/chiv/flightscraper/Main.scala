package io.chiv.flightscraper

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import io.chiv.flightscraper.config.{Config, SearchConfig}
import io.chiv.flightscraper.emailer.EmailClient
import io.chiv.flightscraper.kayak.KayakClient
import io.chiv.flightscraper.selenium.WebDriver
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import cats.syntax.traverse._
import cats.instances.list._

object Main extends IOApp {

  implicit val logger: SelfAwareStructuredLogger[IO] =
    Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {

    def app: IO[Unit] =
      for {
        config <- Config.load()
        searches <- SearchConfig
                     .load()
        webDriver = WebDriver.resource(
          config.geckoDriverLocation,
          headless = true
        )
        emailClient  = EmailClient(config.emailAccessKey, config.emailSecretKey, config.emailAddress)
        kayakClient  = KayakClient.apply(webDriver, emailClient)
        processor    = FlightSearcher(kayakClient)
        lowestPrices <- processor.process(searches)
        _            <- logger.info(s"lowest prices obtained: ${lowestPrices.mkString(",\n")}")
        _            <- lowestPrices.toList.traverse { case (search, (paramGrouping, price)) => emailClient.sendNotification(search, price, paramGrouping) }
        _            <- app //repeat
      } yield ()

    app.map(_ => ExitCode.Success)

  }
}
