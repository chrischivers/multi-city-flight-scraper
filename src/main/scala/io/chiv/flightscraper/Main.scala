package io.chiv.flightscraper

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder, AmazonDynamoDBLockClient}
import io.chiv.flightscraper.config.{Config, SearchConfig}
import io.chiv.flightscraper.db.{DB, DynamoDb}
import io.chiv.flightscraper.emailer.EmailClient
import io.chiv.flightscraper.kayak.KayakClient
import io.chiv.flightscraper.selenium.WebDriver
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

object Main extends IOApp {

  implicit val logger: SelfAwareStructuredLogger[IO] =
    Slf4jLogger.getLogger[IO]

  def app(dynamoDb: DynamoDB, lockClient: AmazonDynamoDBLockClient): IO[Unit] =
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
      dbClient: DB = DynamoDb(dynamoDb, lockClient)
      processor    = FlightSearcher(kayakClient, emailClient, dbClient, searches)
      _            <- processor.processNext()
      _            <- app(dynamoDb, lockClient) //repeat
    } yield ()

  private def resources: Resource[IO, (DynamoDB, AmazonDynamoDBLockClient)] = {

    def amazonDynamoDbClientResource: Resource[IO, AmazonDynamoDB] =
      Resource.make(
        IO(
          AmazonDynamoDBClientBuilder.standard
            .withRegion("eu-west-2")
            .build
        )
      )(client => IO(client.shutdown()))

    for {
      amazonDynamoDbClientResource <- amazonDynamoDbClientResource
      dynamoDB                     <- DynamoDb.dynamoDBResource(amazonDynamoDbClientResource)
      lockClient                   <- DynamoDb.lockClientResource(amazonDynamoDbClientResource)
    } yield (dynamoDB, lockClient)
  }

  override def run(args: List[String]): IO[ExitCode] =
    resources
      .use {
        case (dynamoDb, lockClient) =>
          app(dynamoDb, lockClient)
      }
      .map(_ => ExitCode.Success)

}
