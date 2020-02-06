package io.chiv.flightscraper.db

import java.util.concurrent.Executors

import cats.data.NonEmptyList
import cats.effect.{Blocker, IO, Resource}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2.{AmazonDynamoDBClientBuilder, AmazonDynamoDBLockClient, CreateDynamoDBTableOptions}
import io.chiv.flightscraper.Main
import io.chiv.flightscraper.db.DynamoDb.Resources
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.util.TestGenerators
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class DynamoDbTest extends WordSpec with Matchers with TypeCheckedTripleEquals with TestGenerators {

  implicit val ec     = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))
  implicit val cs     = IO.contextShift(ec)
  implicit val timer  = IO.timer(ec)
  implicit val logger = Main.logger

  "dynamo db" should {
    "return none if there are no records left to process" in {

      setupDynamoClient
        .use { dynamo =>
          for {
            nextParams <- dynamo.nextParamsToProcess
            _          = nextParams should ===(None)
          } yield ()
        }
        .unsafeRunSync()
    }

    "set up the table and return the next record" in {

      val searchId = Search.Id("test-string")
      val kpg1     = kayakParamsGrouping()
      val kpg2     = kayakParamsGrouping()
      val data     = Map(searchId -> NonEmptyList.of(kpg1, kpg2))

      setupDynamoClient
        .use { dynamo =>
          for {
            _          <- dynamo.setTable(data)
            nextParams <- dynamo.nextParamsToProcess
            _          = nextParams.get.searchId should ===(searchId)
            _          = nextParams.get.params should (be(kpg1.params) or be(kpg2.params))
          } yield ()
        }
        .unsafeRunSync()
    }

    "not return the same record in a concurrent context when lock is used" in {

      val searchId = Search.Id("test-string")
      val kpg1     = kayakParamsGrouping()
      val data     = Map(searchId -> NonEmptyList.of(kpg1))

      setupDynamoClient
        .use { dynamo =>
          for {
            _           <- dynamo.setTable(data)
            fib1        <- dynamo.withLock(dynamo.nextParamsToProcess).start
            fib2        <- dynamo.withLock(dynamo.nextParamsToProcess).start
            fib3        <- dynamo.withLock(dynamo.nextParamsToProcess).start
            nextParams1 <- fib1.join
            nextParams2 <- fib2.join
            nextParams3 <- fib3.join
            _           = List(nextParams1, nextParams2, nextParams3).count(_.isDefined) should ===(1)
          } yield ()
        }
        .unsafeRunSync()
    }

    "set up the table and return the next record, and not return it again" in {

      val searchId = Search.Id("test-string")
      val kpg1     = kayakParamsGrouping()
      val data     = Map(searchId -> NonEmptyList.of(kpg1))

      setupDynamoClient
        .use { dynamo =>
          for {
            _           <- dynamo.setTable(data)
            nextParams1 <- dynamo.nextParamsToProcess
            _           = nextParams1.isDefined should ===(true)
            nextParams2 <- dynamo.nextParamsToProcess
            _           = nextParams2.isDefined should ===(false)
          } yield ()
        }
        .unsafeRunSync()
    }

    "update a record with price details, and not return it again" in {
      val searchId = Search.Id("test-string")
      val kpg1     = kayakParamsGrouping()
      val kpg2     = kayakParamsGrouping()
      val data     = Map(searchId -> NonEmptyList.of(kpg1, kpg2))

      setupDynamoClient
        .use { dynamo =>
          for {
            _           <- dynamo.setTable(data)
            nextParams1 <- dynamo.nextParamsToProcess
            _           = nextParams1.isDefined should ===(true)
            _           <- dynamo.updatePrice(nextParams1.get.recordId, Some(100))
            nextParams2 <- dynamo.nextParamsToProcess
            _           = nextParams2.isDefined should ===(true)
            _           <- dynamo.updatePrice(nextParams2.get.recordId, None)
            nextParams3 <- dynamo.nextParamsToProcess
            _           = nextParams3.isDefined should ===(false)
          } yield ()
        }
        .unsafeRunSync()
    }

    "return all completed records" in {
      val searchId = Search.Id("test-string")
      val kpg1     = kayakParamsGrouping()
      val kpg2     = kayakParamsGrouping()
      val kpg3     = kayakParamsGrouping()
      val data     = Map(searchId -> NonEmptyList.of(kpg1, kpg2, kpg3))

      setupDynamoClient
        .use { dynamo =>
          for {
            _                <- dynamo.setTable(data)
            nextParams1      <- dynamo.nextParamsToProcess
            _                <- dynamo.updatePrice(nextParams1.get.recordId, Some(100))
            nextParams2      <- dynamo.nextParamsToProcess
            _                <- dynamo.updatePrice(nextParams2.get.recordId, Some(150))
            completedRecords <- dynamo.completedRecords
            _                = completedRecords should have size 2
          } yield ()
        }
        .unsafeRunSync()
    }
  }

  def setupDynamoClient = {

    val amazonDynamoDbClientResource =
      Resource.make(
        IO(
          AmazonDynamoDBClientBuilder.standard
            .withEndpointConfiguration(new EndpointConfiguration("http://localhost:8000", "eu-west-2"))
            .build
        )
      )(client => IO(client.shutdown()))

    (for {
      amazonDynamoDb <- amazonDynamoDbClientResource
      dynamoDB       <- DynamoDb.dynamoDBResource(amazonDynamoDb)
      lockClient     <- DynamoDb.lockClientResource(amazonDynamoDb)
      blocker        <- Blocker[IO]
    } yield Resources(amazonDynamoDb, dynamoDB, lockClient, blocker)).evalMap { resources =>
      for {
        _      <- IO(resources.amazonDynamoDB.deleteTable(DynamoDb.tableName)).attempt
        _      <- IO(resources.amazonDynamoDB.deleteTable("lockTable")).attempt
        _      <- DynamoDb.createTablesIfNotExisting(resources.amazonDynamoDB)
        client <- IO(DynamoDb(resources.dynamoDb, resources.lockClient)(implicitly, implicitly, implicitly, resources.blocker))
      } yield client
    }
  }

}
