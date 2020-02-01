package io.chiv.flightscraper

import cats.effect.{ExitCode, IO, IOApp}
import io.chiv.flightscraper.config.{Config, SearchConfig}
import io.chiv.flightscraper.emailer.EmailClient
import io.chiv.flightscraper.kayak.KayakClient
import io.chiv.flightscraper.selenium.WebDriver
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import sys.process._
import cats.syntax.flatMap._
import cats.syntax.functor._

object Main extends IOApp {

  implicit val logger: SelfAwareStructuredLogger[IO] =
    Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {

    def app: IO[Unit] =
      for {
        _      <- setupDependencies()
        config <- Config.load()
        searches <- SearchConfig
                     .load()
        webDriver = WebDriver.resource(
          config.geckoDriverLocation,
          headless = true
        )
        emailClient = EmailClient(config.emailAccessKey, config.emailSecretKey, config.emailAddress)
        kayakClient = KayakClient.apply(webDriver, emailClient)
        processor   = FlightSearcher(kayakClient, emailClient)
        _           <- processor.process(searches)
        _           <- app //repeat
      } yield ()

    app.map(_ => ExitCode.Success)

  }

  def setupDependencies(): IO[Unit] =
    IO("wget https://github.com/mozilla/geckodriver/releases/download/v0.26.0/geckodriver-v0.26.0-linux64.tar.gz -O /tmp/geckodriver.tar.gz".!) >>
      IO("tar xzf /tmp/geckodriver.tar.gz".!) >>
      IO("mkdir /tmp/selenium".!) >>
      IO("wget https://selenium-release.storage.googleapis.com/3.141/selenium-server-standalone-3.141.59.jar -O /tmp/selenium.jar".!) >>
      IO("wget http://www.java2s.com/Code/JarDownload/testng/testng-6.5.1.jar.zip -O /tmp/testng.jar".!) >>
      IO("unzip /tmp/testng.jar.zip".!) >>
      IO("screen -d -m -S selenium bash -c 'DISPLAY=:1 xvfb-run java -jar /tmp/selenium.jar'".!).void

}
