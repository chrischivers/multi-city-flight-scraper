package io.chiv.flightscraper.kayak

import java.time.Instant

import cats.data.{NonEmptyList, OptionT}
import cats.effect.{IO, Resource, Timer}
import cats.syntax.flatMap._
import io.chiv.flightscraper.emailer.EmailClient
import io.chiv.flightscraper.model.Model.{AirlineCode, AirportCode}
import io.chiv.flightscraper.selenium.WebDriver
import io.chiv.flightscraper.util._
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration.{FiniteDuration, _}

trait KayakClient {
  def getLowestPrice(paramsGrouping: KayakParamsGrouping,
                     airlineFilter: Option[NonEmptyList[AirlineCode]],
                     layoverFilter: Option[AirportCode]): IO[Option[Int]]
}

object KayakClient {

  def apply(driverResource: Resource[IO, WebDriver], emailClient: EmailClient)(implicit timer: Timer[IO], logger: Logger[IO]) = {

    val maxLoadWaitTime: FiniteDuration              = 4.minutes
    val timeBetweenLoadReadyAttempts: FiniteDuration = 10.seconds

    new KayakClient {

      override def getLowestPrice(paramsGrouping: KayakParamsGrouping,
                                  airlineFilter: Option[NonEmptyList[AirlineCode]],
                                  layoverFilter: Option[AirportCode]): IO[Option[Int]] =
        driverResource.use { driver =>
          val url = paramsGrouping.toUri(airlineFilter, layoverFilter)

          val process = for {
            _           <- logger.info(s"Looking up price for url $url")
            _           <- driver.setUrl(url)
            _           <- waitToBeReady(driver)
            lowestPrice <- extractLowestPrice(driver)
            _           <- logger.info(s"Lowest price obtained for $url was $lowestPrice")
          } yield lowestPrice
          process.withBackoffRetry(2.hours, 8)
        }

      private def waitToBeReady(webDriver: WebDriver): IO[Unit] = {
        val startTime = Instant.now

        def helper: IO[Unit] =
          (for {
            pageLoaded <- webDriver
                           .findElementsByXPath("//div[@style='transform: translateX(100%);']")
                           .map(_.nonEmpty)
            captchaDisplayed <- webDriver
                                 .findElementsByXPath("//*[contains(text(),'Please confirm that you are a real KAYAK user')]")
                                 .map(_.nonEmpty)
          } yield (pageLoaded, captchaDisplayed)).flatMap {
            case (true, _) => IO.unit
            case (false, false) if (Instant.now.toEpochMilli - startTime.toEpochMilli) < maxLoadWaitTime.toMillis =>
              IO.sleep(timeBetweenLoadReadyAttempts) >> helper
            case (false, true) => IO.raiseError(new RuntimeException("Captcha encountered while waiting for page to load"))
            case _ =>
              IO.raiseError(
                new RuntimeException("Wait condition not satisfied")
              )
          }

        logger
          .info(s"Waiting for page to load (max wait time $maxLoadWaitTime)") >> helper >> IO
          .sleep(3.seconds)
      }

      private def extractLowestPrice(webDriver: WebDriver): IO[Option[Int]] =
        (for {
          elems <- OptionT.liftF(
                    webDriver.findElementsByXPath(
                      "//span[@class='price-text' and not(contains(@id,'extra-info'))]"
                    )
                  )
          firstElemText <- OptionT.fromOption[IO](elems.headOption)
          priceStr      <- OptionT.liftF(firstElemText.text)
          price         <- OptionT.liftF(IO(priceStr.dropWhile(_ == '£').toInt))

        } yield price).value

//      private def writeScreenshot(screenshot: Screenshot): IO[Unit] =
//        Resource.make(IO(new BufferedOutputStream(new FileOutputStream("error-screenshot.jpg"))))(bos => IO(bos.close())).use { bos =>
//          IO(bos.write(screenshot.value))
//        }
    }
  }

}
