package io.chiv.flightscraper

import java.util.concurrent.{Executors, TimeUnit}

import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource, SyncIO}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDB, AmazonDynamoDBClientBuilder, AmazonDynamoDBLockClient}
import io.chiv.flightscraper.config.{Config, SearchConfig}
import io.chiv.flightscraper.db.DynamoDb.Resources
import io.chiv.flightscraper.db.{DB, DynamoDb}
import io.chiv.flightscraper.emailer.EmailClient
import io.chiv.flightscraper.kayak.KayakClient
import io.chiv.flightscraper.selenium.WebDriver
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object Main extends IOApp.WithContext {

  implicit val logger: SelfAwareStructuredLogger[IO] =
    Slf4jLogger.getLogger[IO]

  override protected def executionContextResource: Resource[SyncIO, ExecutionContext] =
    Resource
      .make(SyncIO(Executors.newFixedThreadPool(8)))(
        pool =>
          SyncIO {
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.SECONDS)
        }
      )
      .map(ExecutionContext.fromExecutorService)

  def app(dynamoResources: Resources): IO[Unit] =
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
      dbClient: DB = DynamoDb(dynamoResources.dynamoDb, dynamoResources.lockClient)(implicitly, implicitly, implicitly, dynamoResources.blocker)
      _            <- DynamoDb.createTablesIfNotExisting(dynamoResources.amazonDynamoDB)
      processor    = FlightSearcher(kayakClient, emailClient, dbClient, searches)
      _            <- processor.processNext()
    } yield ()

  private def resources: Resource[IO, Resources] = {

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
      blocker                      <- Blocker[IO]
    } yield Resources(amazonDynamoDbClientResource, dynamoDB, lockClient, blocker)
  }

  override def run(args: List[String]): IO[ExitCode] =
    resources
      .use(app)
      .map(_ => ExitCode.Success)

}
