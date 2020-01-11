package io.chiv.flightscraper

import java.io.File

import cats.Monad
import cats.effect.IO
import io.chiv.flightscraper.model.Model._
import io.chiv.flightscraper.model.Search
import io.circe.Decoder
import io.circe.jawn.JawnParser
import io.circe.generic.semiauto._

object SearchConfig {

  def load(file: File): IO[List[Search]] = {
    new JawnParser()
      .parseFile(file)
      .flatMap(_.as[List[Search]])
      .fold(
        err => IO.raiseError[List[Search]](err),
        searches => IO.pure(searches)
      )
  }

  def load(): IO[List[Search]] =
    load(new File(getClass.getResource("/search-config.json").getFile))
}
