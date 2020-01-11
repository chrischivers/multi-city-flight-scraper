package io.chiv.flightscraper.kayak

import java.io.{BufferedOutputStream, FileOutputStream}
import java.time.{Instant, LocalDate}

import cats.data.OptionT
import cats.effect.{IO, Resource, Timer}
import cats.syntax.flatMap._
import io.chiv.flightscraper.model.Model.AirportCode
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.util._
import io.chiv.flightscraper.selenium.WebDriver
import io.chiv.flightscraper.selenium.WebDriver.Screenshot
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration.{FiniteDuration, _}

trait KayakClient {
  def getLowestPrice(paramsGrouping: KayakParamsGrouping): IO[Option[Int]]
}

object KayakClient {

  def apply(driverResource: Resource[IO, WebDriver])(implicit timer: Timer[IO], logger: Logger[IO]) = {

    val maxLoadWaitTime: FiniteDuration              = 3.minutes
    val timeBetweenLoadReadyAttempts: FiniteDuration = 10.seconds

    new KayakClient {

      override def getLowestPrice(paramsGrouping: KayakParamsGrouping): IO[Option[Int]] =
        driverResource.use { driver =>
          val url = paramsGrouping.toUri

          for {
            _           <- logger.info(s"Looking up price for url ${url.toString()}")
            _           <- driver.setUrl(url)
            _           <- waitToBeReady(driver).withRetry(3)
            lowestPrice <- extractLowestPrice(driver)
          } yield lowestPrice
        }

      private def waitToBeReady(webDriver: WebDriver): IO[Unit] = {
        val startTime = Instant.now

        def helper: IO[Unit] =
          webDriver
            .findElementsByXPath("//div[@style='transform: translateX(100%);']")
            .map(_.nonEmpty)
            .flatMap {
              case true => IO.unit
              case false if (Instant.now.toEpochMilli - startTime.toEpochMilli) < maxLoadWaitTime.toMillis =>
                IO.sleep(timeBetweenLoadReadyAttempts) >> helper
              case _ =>
                webDriver.takeScreenshot.flatMap(writeScreenshot) >>
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

      private def writeScreenshot(screenshot: Screenshot) = IO {
        val bos =
          new BufferedOutputStream(new FileOutputStream("screenshot.jpg"))
        bos.write(screenshot.value)
        bos.close() // You may end up with 0 bytes file if not calling close.
      }
    }
  }

}
