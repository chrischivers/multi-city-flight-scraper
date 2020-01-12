package io.chiv.flightscraper.emailer

import cats.effect.IO
import com.mailjet.client.resource.Emailv31
import com.mailjet.client.{ClientOptions, MailjetClient, MailjetRequest}
import io.chiv.flightscraper.kayak.KayakParamsGrouping
import io.chiv.flightscraper.model.Search
import io.chrisdavenport.log4cats.Logger
import org.json.{JSONArray, JSONObject}

trait EmailClient {
  def sendNotification(search: Search, lowestPrice: Int, kayakParamsGrouping: KayakParamsGrouping): IO[Unit]
}

object EmailClient {
  def apply(accessKey: String, secretKey: String)(implicit logger: Logger[IO]) = new EmailClient {
    override def sendNotification(search: Search, lowestPrice: Int, kayakParamsGrouping: KayakParamsGrouping): IO[Unit] =
      IO {
        val client = new MailjetClient(accessKey, secretKey, new ClientOptions("v3.1"))
        val request = new MailjetRequest(Emailv31.resource)
          .property(
            Emailv31.MESSAGES,
            new JSONArray()
              .put(
                new JSONObject()
                  .put(Emailv31.Message.FROM,
                       new JSONObject()
                         .put("Email", "chrischivers@gmail.com")
                         .put("Name", "Chris"))
                  .put(Emailv31.Message.TO,
                       new JSONArray()
                         .put(
                           new JSONObject()
                             .put("Email", "chrischivers@gmail.com")
                             .put("Name", "Chris")
                         ))
                  .put(Emailv31.Message.SUBJECT, "Multi city flight scraper notification")
                  .put(Emailv31.Message.TEXTPART, s"Lowest price found for search $search. Price: $lowestPrice. Params: $kayakParamsGrouping")
                  .put(Emailv31.Message.HTMLPART,
                       s"<p>Lowest price found for search $search.</p><p>Price: $lowestPrice.</p><p>Params: $kayakParamsGrouping</p>")
              )
          )
        client.post(request)
      }.flatMap { response =>
        logger.info(s"Email sent. Response: ${response.getStatus}. Data: ${response.getData}")
      }
  }
}
