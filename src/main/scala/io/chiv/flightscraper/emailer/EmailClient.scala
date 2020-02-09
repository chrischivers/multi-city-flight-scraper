package io.chiv.flightscraper.emailer

import cats.data.NonEmptyList
import cats.effect.{IO, Timer}
import com.mailjet.client.errors.MailjetRateLimitException
import com.mailjet.client.resource.Emailv31
import com.mailjet.client.{ClientOptions, MailjetClient, MailjetRequest}
import io.chiv.flightscraper.kayak.KayakParamsGrouping
import io.chiv.flightscraper.model.Model.Price
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.selenium.WebDriver.Screenshot
import io.chrisdavenport.log4cats.Logger
import org.json.{JSONArray, JSONObject}
import io.chiv.flightscraper.util._

import scala.concurrent.duration._

trait EmailClient {
  def sendNotification(search: Search, lowestPrices: NonEmptyList[(Price, KayakParamsGrouping)]): IO[Unit]
  def sendError(screenshot: Screenshot): IO[Unit]
}

object EmailClient {
  def apply(accessKey: String, secretKey: String, emailAddress: String)(implicit logger: Logger[IO], timer: Timer[IO]) = new EmailClient {

    private def send(subject: String, htmlBody: Option[String], attachmentArray: Option[JSONArray]) = {
      val client = new MailjetClient(accessKey, secretKey, new ClientOptions("v3.1"))
      val request = new MailjetRequest(Emailv31.resource)
        .property(
          Emailv31.MESSAGES,
          new JSONArray()
            .put {
              val jsonObject = new JSONObject()
                .put(Emailv31.Message.FROM,
                     new JSONObject()
                       .put("Email", emailAddress)
                       .put("Name", "CC"))
                .put(Emailv31.Message.TO,
                     new JSONArray()
                       .put(
                         new JSONObject()
                           .put("Email", emailAddress)
                           .put("Name", "CC")
                       ))
                .put(Emailv31.Message.SUBJECT, subject)
                .put(Emailv31.Message.TEXTPART, "N/A")

              htmlBody.foreach(jsonObject.put(Emailv31.Message.HTMLPART, _))
              attachmentArray.foreach(jsonObject.put(Emailv31.Message.ATTACHMENTS, _))

              jsonObject
            }
        )
      IO(client.post(request))
    }.flatMap { response =>
        logger.info(s"Email sent. Response: ${response.getStatus}. Data: ${response.getData}")
      }
      .withBackoffRetry(2.hours, 10)

    override def sendNotification(search: Search, lowestPrices: NonEmptyList[(Price, KayakParamsGrouping)]): IO[Unit] =
      send(
        s"Multi city flight scraper: ${search.name}. Lowest price £${lowestPrices.head._1.value}",
        Some(
          s"<p>Lowest prices found for search [${search.name}].</p>" +
            lowestPrices.toList.map(p => s"<p>Price: £${p._1.value}}.</br>Params: ${p._2.toString}</p>").mkString("\n")
        ),
        None
      )

    override def sendError(screenshot: Screenshot): IO[Unit] = {

      val fileData = com.mailjet.client.Base64.encode(screenshot.value)
      val attachment = new JSONArray().put(
        new JSONObject()
          .put("ContentType", "image/jpeg")
          .put("Filename", "error-screenshot.jpg")
          .put("Base64Content", fileData)
      )
      send("Multi city flight scraper notification: ERROR", None, Some(attachment))
    }
  }
}
