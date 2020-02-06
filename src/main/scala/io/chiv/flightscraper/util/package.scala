package io.chiv.flightscraper

import java.time.LocalDate

import cats.effect.{ContextShift, IO, Timer}
import cats.syntax.flatMap._
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.CancellationException
import scala.concurrent.duration._

package object util {

  def datesBetween(from: LocalDate, to: LocalDate): List[LocalDate] =
    from.toEpochDay.to(to.toEpochDay).map(LocalDate.ofEpochDay).toList

  implicit class IOOps[T](io: IO[T])(implicit logger: Logger[IO]) {
    def withRetry(attempts: Int): IO[T] =
      io.attempt.flatMap {
        case Left(err) =>
          logger.error(err.getMessage) >>
            (if (attempts > 1) {
               logger.info(s"retrying another ${attempts - 1} times") >> withRetry(
                 attempts - 1
               )
             } else logger.info("No more retries") >> IO.raiseError(err))
        case Right(t) => IO.pure(t)
      }

    def retryIf(attempts: Int, retryPredicate: T => Boolean): IO[T] =
      io.flatMap { t =>
        if (retryPredicate(t) && attempts > 1)
          logger.info(s"retrying another ${attempts - 1} times as retry predicate is true") >> retryIf(attempts - 1, retryPredicate)
        else if (retryPredicate(t))
          logger.info("No more retries, returning original value") >> IO.pure(t)
        else IO.pure(t)
      }

    def withBackoffRetry(maxDelay: FiniteDuration, multiplier: Double, attemptNumber: Int = 1)(implicit timer: Timer[IO]): IO[T] = {

      val delayInSec  = (Math.pow(2.0, attemptNumber) - 1.0) * .5
      val backOffWait = Math.round(Math.min(delayInSec * multiplier, maxDelay.toSeconds))
      io.attempt.flatMap {
        case Left(err) =>
          logger.error(err.getMessage) >>
            logger.info(s"retrying another time with backoff wait of $backOffWait seconds") >> IO.sleep(backOffWait.seconds) >> withBackoffRetry(
            maxDelay,
            multiplier,
            attemptNumber + 1
          )
        case Right(t) => IO.pure(t)
      }
    }

    def withTimeout(
      timeoutAfter: FiniteDuration
    )(implicit timer: Timer[IO], contextShift: ContextShift[IO]): IO[T] =
      IO.race(io, timer.sleep(timeoutAfter)).flatMap {
        case Left(r) => IO.pure(r)
        case Right(_) =>
          IO.raiseError(
            new CancellationException(s"IO timed out after $timeoutAfter")
          )
      }
  }
}
