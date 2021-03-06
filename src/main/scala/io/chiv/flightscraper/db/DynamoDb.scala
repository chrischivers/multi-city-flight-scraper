package io.chiv.flightscraper.db
import java.util.UUID
import java.util.concurrent.TimeUnit

import cats.data.{NonEmptyList, OptionT}
import cats.effect.{Blocker, Clock, ContextShift, IO, Resource, Timer}
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.document.{DynamoDB, Item, PrimaryKey, Table}
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.dynamodbv2._
import io.chiv.flightscraper.db.DB.{RecordId, RecordStatus}
import io.chiv.flightscraper.kayak.{KayakParams, KayakParamsGrouping}
import io.chiv.flightscraper.model.Model.Price
import io.chiv.flightscraper.model.{Model, Search}
import io.chrisdavenport.log4cats.Logger
import io.circe.parser._
import io.circe.syntax._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try

object DynamoDb {

  type Before = Long

  case class Resources(amazonDynamoDB: AmazonDynamoDB, dynamoDb: DynamoDB, lockClient: AmazonDynamoDBLockClient, blocker: Blocker)

  protected[db] val primaryTableName = "searches"
  protected[db] val lockTableName    = "lockTable"

  private object Table {
    val recordId     = "record_id"
    val searchId     = "search_id"
    val searchStatus = "search_status"
    val searchParams = "search_params"
    val price        = "price"
    val timestamp    = "update_timestamp"
  }

  def dynamoDBResource(amazonDynamoDB: AmazonDynamoDB) = Resource.make(IO((new DynamoDB(amazonDynamoDB))))(c => IO(c.shutdown()))

  def lockClientResource(amazonDynamoDb: AmazonDynamoDB) =
    Resource.make(
      IO(
        new AmazonDynamoDBLockClient(
          AmazonDynamoDBLockClientOptions
            .builder(amazonDynamoDb, lockTableName)
            .withTimeUnit(TimeUnit.SECONDS)
            .withLeaseDuration(30L)
            .withHeartbeatPeriod(3L)
            .withCreateHeartbeatBackgroundThread(true)
            .withPartitionKeyName("key")
            .build()
        )
      )
    )(x => IO(x.close()))

  def createTablesIfNotExisting(amazonDynamoDB: AmazonDynamoDB)(implicit logger: Logger[IO], timer: Timer[IO]) = {

    val keySchema            = List(new KeySchemaElement().withAttributeName(Table.recordId).withKeyType(KeyType.HASH)).asJava
    val attributeDefinitions = List(new AttributeDefinition().withAttributeName(Table.recordId).withAttributeType("S")).asJava

    def awaitTableToBeReady(tableName: String): IO[Unit] =
      IO(amazonDynamoDB.describeTable(tableName).getTable.getTableStatus).flatMap {
        case "ACTIVE" => IO.unit
        case _        => IO.sleep(5.seconds) >> awaitTableToBeReady(tableName)
      }

    for {
      _ <- IO(
            AmazonDynamoDBLockClient.createLockTableInDynamoDB(
              CreateDynamoDBTableOptions
                .builder(amazonDynamoDB,
                         new ProvisionedThroughput()
                           .withReadCapacityUnits(5L)
                           .withWriteCapacityUnits(6L),
                         lockTableName)
                .build()
            )
          ).handleErrorWith {
            case err: ResourceInUseException => logger.info(s"Not creating table $lockTableName as already exists")
            case err                         => logger.info(err)("Error thrown when attempting to create table. Moving on.")
          }
      _ <- awaitTableToBeReady(lockTableName)
      _ <- IO {
            amazonDynamoDB
              .createTable(
                new CreateTableRequest()
                  .withTableName(DynamoDb.primaryTableName)
                  .withKeySchema(keySchema)
                  .withAttributeDefinitions(attributeDefinitions)
                  .withProvisionedThroughput(
                    new ProvisionedThroughput()
                      .withReadCapacityUnits(5L)
                      .withWriteCapacityUnits(6L)
                  )
              )
          }.handleErrorWith {
            case err: ResourceInUseException => logger.info(s"Not creating table $primaryTableName as already exists")
            case err                         => logger.info(err)("Error thrown when attempting to create table. Moving on.")
          }
      _ <- awaitTableToBeReady(DynamoDb.primaryTableName)
    } yield ()
  }

  def apply(
    dynamoDb: DynamoDB,
    lockClient: AmazonDynamoDBLockClient
  )(implicit clock: Clock[IO], timer: Timer[IO], logger: Logger[IO], cs: ContextShift[IO], blocker: Blocker) = new DB {

    private def scanTable(table: Table, statusFilter: Map[RecordStatus, Option[Before]]) = {

      val filtersWithIndex: Map[(RecordStatus, Option[Before]), Int] = statusFilter.zipWithIndex
      val filterExpression = filtersWithIndex
        .map {
          case ((_, before), index) =>
            s"${Table.searchStatus} = :status$index${before.fold("")(_ => s" AND ${Table.timestamp} < :update_timestamp$index")}"
        }
        .mkString(" OR ")
      val valueMap = filtersWithIndex.foldLeft(new ValueMap()) {
        case (vm, ((status, before), index)) =>
          val v = vm.withString(s":status$index", status.value)
          before.fold(v)(b => v.withLong(s":update_timestamp$index", b))
      }

      val scan =
        if (statusFilter.isEmpty) new ScanSpec()
        else {
          new ScanSpec()
            .withFilterExpression(filterExpression)
            .withValueMap(valueMap)
        }

      IO(table.scan(scan).iterator().asScala.toList)
    }

    private def parseItem: Item => Either[String, (KayakParamsGrouping.WithRecordId, Option[Price])] =
      item =>
        for {
          recordId <- Option(item.getString(Table.recordId)).toRight(s"Unable to get string ${Table.recordId}")
          searchId <- Option(item.getString(Table.searchId)).toRight(s"Unable to get string ${Table.searchId}")
          params <- Option(item.getJSON(Table.searchParams))
                     .toRight(s"Unable to get string ${Table.searchParams}")
                     .flatMap(parse(_).leftMap(err => s"Parsing failure in ${Table.searchParams} json ${err.message}"))
                     .flatMap(_.as[List[KayakParams]].leftMap(err => s"Decoding failure in ${Table.searchParams} json ${err.message}"))
                     .flatMap(NonEmptyList.fromList(_).toRight(s"Empty ${Table.searchParams} list returned"))
          price = Try(item.getInt(Table.price)).toOption.map(Price.apply)
        } yield (KayakParamsGrouping.WithRecordId(Search.Id(searchId), params, RecordId(recordId)), price)

    override def nextParamsToProcess(inProgressTimeout: Duration = 30.minutes): IO[Option[KayakParamsGrouping.WithRecordId]] =
      withTable { table =>
        clock.realTime(MILLISECONDS).flatMap { now =>
          val timeout = now - inProgressTimeout.toMillis
          scanTable(table, Map(RecordStatus.Open -> None, RecordStatus.InProgress -> Some(timeout))).flatMap { items =>
            OptionT
              .fromOption[IO](items.headOption)
              .semiflatMap { item =>
                parseItem(item).fold(str => IO.raiseError(new RuntimeException(s"Error: $str")), r => IO.pure(r._1))
              }
              .semiflatMap { item =>
                clock.realTime(MILLISECONDS).flatMap { now =>
                  val expressionAttributeNames = Map[String, String]("#S" -> Table.searchStatus, "#T" -> Table.timestamp).asJava
                  val expressionAttributeValues =
                    Map[String, Object](":status" -> RecordStatus.InProgress.value, ":timestamp" -> Long.box(now)).asJava

                  IO(
                    table.updateItem(new PrimaryKey(Table.recordId, item.recordId.value),
                                     "set #S=:status, #T=:timestamp",
                                     expressionAttributeNames,
                                     expressionAttributeValues)
                  ).map(_ => item)
                }
              }
              .value
          }
        }
      }

    override def completedRecords: IO[List[(KayakParamsGrouping.WithRecordId, Option[Model.Price])]] =
      withTable { table =>
        scanTable(table, Map(RecordStatus.Completed -> None))
          .flatMap(list => list.traverse(item => IO.fromEither(parseItem(item).leftMap(str => new RuntimeException(s"Error: $str")))))
      }

    override def updatePrice(recordId: DB.RecordId, lowestPrice: Option[Int]): IO[Unit] =
      withTable { table =>
        clock.realTime(MILLISECONDS).flatMap { now =>
          val expressionAttributeNames =
            Map[String, String]("#P" -> Table.price, "#S" -> Table.searchStatus, "#R" -> Table.recordId, "#T" -> Table.timestamp).asJava
          val expressionAttributeValues =
            Map[String, Object](":price"     -> lowestPrice.map(Int.box).orNull,
                                ":status"    -> RecordStatus.Completed.value,
                                ":record_id" -> recordId.value,
                                ":timestamp" -> Long.box(now)).asJava

          IO(
            table.updateItem(
              new PrimaryKey(Table.recordId, recordId.value),
              "set #P=:price, #S=:status, #T=:timestamp",
              "#R=:record_id",
              expressionAttributeNames,
              expressionAttributeValues
            )
          )
        }

      }.void

    override def setSearchData(data: Map[Search.Id, NonEmptyList[KayakParamsGrouping.WithoutRecordId]]): IO[Unit] =
      withTable { table =>
        data.toList.traverse {
          case (searchId, paramsGrouping) =>
            paramsGrouping.toList.traverse { pg =>
              val paramsJsonList = pg.params.toList.asJson.noSpaces
              val item = new Item()
                .withPrimaryKey(Table.recordId, UUID.randomUUID().toString)
                .withString(Table.searchId, searchId.value)
                .withString(Table.searchStatus, RecordStatus.Open.value)
                .withJSON(Table.searchParams, paramsJsonList)

              IO(table.putItem(item))

            }

        }.void
      }

    override def wipeData: IO[Unit] = withTable { table =>
      withTable { table =>
        scanTable(table, Map.empty).flatMap { list =>
          list.traverse { item =>
            IO(Option(item.getString(Table.recordId)).get).flatMap { recordId =>
              IO(table.deleteItem(Table.recordId, recordId)).void
            }
          }.void
        }
      }
    }

    private def withTable[T](f: Table => IO[T]): IO[T] =
      IO(dynamoDb.getTable(primaryTableName)).flatMap(f)

    override def withLock[T](f: IO[T]): IO[T] = {
      val options = AcquireLockOptions
        .builder("key")
        .build()

      def acquireLock: IO[LockItem] =
        blocker.blockOn {
          IO(lockClient.tryAcquireLock(options)).flatMap { result =>
            if (result.isPresent)
              logger.info("Acquired DB Lock") >> IO(result.get())
            else logger.info("Waiting for DB Lock") >> IO.sleep(1.second) >> acquireLock
          }
        }

      Resource.make(acquireLock)(lockItem => IO(lockClient.releaseLock(lockItem))).use(_ => f)

    }
  }
}
