package io.chiv.flightscraper.selenium

import cats.effect.{IO, Resource}
import io.chiv.flightscraper.selenium.WebDriver.Screenshot
import org.http4s.Uri
import org.openqa.selenium.{By, OutputType}
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions}

import scala.collection.JavaConverters._

trait WebDriver {
  def setUrl(uri: Uri): IO[Unit]
  def findElementsByClassName(className: String): IO[List[WebElement]]
  def findElementsByXPath(xPath: String): IO[List[WebElement]]
  def getCurrentUrl: IO[Uri]
  def takeScreenshot: IO[Screenshot]
}

object WebDriver {

  case class Screenshot(value: Array[Byte]) {
    override def equals(obj: Any): Boolean = obj match {
      case Screenshot(arr) =>
        arr.toList == value.toList //required to assert equality in testing
      case _ => false
    }
  }

  private def firefoxDriverResource(headless: Boolean) =
    Resource.make[IO, FirefoxDriver](IO {
      val options = new FirefoxOptions()
      options.setHeadless(headless)
      new FirefoxDriver(options)
    })(driver => IO(driver.close()))

  def resource(geckoDriverPath: String, headless: Boolean = false) =
    for {
      _ <- Resource.liftF(
            IO(System.setProperty("webdriver.gecko.driver", geckoDriverPath))
          )
      firefoxDriver <- firefoxDriverResource(headless)

    } yield
      new WebDriver {
        override def setUrl(uri: Uri): IO[Unit] =
          IO(firefoxDriver.get(uri.renderString))

        override def findElementsByClassName(
          className: String
        ): IO[List[WebElement]] =
          IO {
            firefoxDriver
              .findElementsByClassName(className)
              .asScala
              .toList
              .map(WebElement.apply)
          }

        override def getCurrentUrl: IO[Uri] =
          IO.fromEither(Uri.fromString(firefoxDriver.getCurrentUrl))

        override def findElementsByXPath(xPath: String): IO[List[WebElement]] =
          IO(
            firefoxDriver
              .findElements(By.xpath(xPath))
              .asScala
              .toList
              .map(WebElement.apply)
          )

        override def takeScreenshot: IO[Screenshot] =
          IO(firefoxDriver.getScreenshotAs(OutputType.BYTES)).map(Screenshot)
      }

}
