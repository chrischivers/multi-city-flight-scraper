package io.chiv.flightscraper.selenium

import cats.effect.IO
import org.openqa.selenium.{By, WebElement => SeleniumWebElement}
import scala.collection.JavaConverters._

trait WebElement {
  def text: IO[String]
  def getAttribute(attrName: String): IO[String]
  def findElementByClassName(className: String): IO[WebElement]
  def findElementsByClassName(className: String): IO[List[WebElement]]
}

object WebElement {
  def apply(seleniumElement: SeleniumWebElement): WebElement = new WebElement {
    override def text: IO[String] = IO(seleniumElement.getText)

    override def getAttribute(attrName: String): IO[String] =
      IO(seleniumElement.getAttribute(attrName))

    override def findElementByClassName(className: String): IO[WebElement] =
      IO(seleniumElement.findElement(By.className(className)))
        .map(WebElement.apply)

    override def findElementsByClassName(
      className: String
    ): IO[List[WebElement]] =
      IO(
        seleniumElement
          .findElements(By.className(className))
          .asScala
          .toList
          .map(WebElement.apply)
      )
  }
}
