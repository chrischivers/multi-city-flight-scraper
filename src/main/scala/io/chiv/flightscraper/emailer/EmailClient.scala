package io.chiv.flightscraper.emailer

import cats.effect.IO
import com.mailjet.client.resource.Emailv31
import com.mailjet.client.{ClientOptions, MailjetClient, MailjetRequest}
import io.chiv.flightscraper.kayak.KayakParamsGrouping
import io.chiv.flightscraper.model.Search
import io.chiv.flightscraper.selenium.WebDriver.Screenshot
import io.chrisdavenport.log4cats.Logger
import org.json.{JSONArray, JSONObject}

trait EmailClient {
  def sendNotification(search: Search, lowestPrice: Int, kayakParamsGrouping: KayakParamsGrouping): IO[Unit]
  def sendError(screenshot: Screenshot): IO[Unit]
}

object EmailClient {
  def apply(accessKey: String, secretKey: String, emailAddress: String)(implicit logger: Logger[IO]) = new EmailClient {

    private def send(subject: String, htmlBody: Option[String], attachmentArray: Option[JSONArray]) =
      IO {
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

                htmlBody.foreach(body => jsonObject.put(Emailv31.Message.HTMLPART, body))
                attachmentArray.foreach(arr => jsonObject.put(Emailv31.Message.ATTACHMENTS, arr))

                jsonObject
              }
          )
        client.post(request)
      }.flatMap { response =>
        logger.info(s"Email sent. Response: ${response.getStatus}. Data: ${response.getData}")
      }

    override def sendNotification(search: Search, lowestPrice: Int, kayakParamsGrouping: KayakParamsGrouping): IO[Unit] =
      send(
        "Multi city flight scraper notification",
        Some(s"<p>Lowest price found for search $search.</p><p>Price: $lowestPrice.</p><p>Params: $kayakParamsGrouping</p>"),
        None
      )

    override def sendError(screenshot: Screenshot): IO[Unit] = {

      val fileData = com.mailjet.client.Base64.encode(screenshot.value)
      val attachment = new JSONArray().put(
        new JSONObject()
          .put("ContentType", "image/jpeg")
          .put("Filename", "error-screenshot.pdf")
          .put("Base64Content", fileData)
      )
      send("Multi city flight scraper notification: ERROR", None, Some(attachment))
    }
  }
}
