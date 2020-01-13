package io.chiv.flightscraper.kayak

import java.io.{BufferedOutputStream, FileOutputStream}
import java.time.Instant

import cats.data.OptionT
import cats.effect.{IO, Resource, Timer}
import cats.syntax.flatMap._
import io.chiv.flightscraper.emailer.EmailClient
import io.chiv.flightscraper.selenium.WebDriver
import io.chiv.flightscraper.selenium.WebDriver.Screenshot
import io.chiv.flightscraper.util._
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.duration.{FiniteDuration, _}

trait KayakClient {
  def getLowestPrice(paramsGrouping: KayakParamsGrouping): IO[Option[Int]]
}

object KayakClient {

  def apply(driverResource: Resource[IO, WebDriver], emailClient: EmailClient)(implicit timer: Timer[IO], logger: Logger[IO]) = {

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
            _           <- logger.info(s"Lowest price obtained for $url was $lowestPrice")
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
                webDriver.takeScreenshot.flatMap(emailClient.sendError) >>
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
