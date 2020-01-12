package io.chiv.flightscraper.config

import cats.effect.IO
import io.chiv.flightscraper.model.Search
import io.circe.parser.parse

import scala.io.Source

object SearchConfig {

  def load(str: String): IO[List[Search]] =
    parse(str)
      .flatMap(_.as[List[Search]])
      .fold(
        err => IO.raiseError[List[Search]](err),
        searches => IO.pure(searches)
      )

  def load(): IO[List[Search]] =
    load(Source.fromResource("search-config.json").getLines().mkString)
}
