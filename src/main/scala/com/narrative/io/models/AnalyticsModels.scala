package com.narrative.io.models

import java.time.ZonedDateTime

sealed trait UserEvent

object UserEvent {
  case object Click extends UserEvent
  case object Impression extends UserEvent

  import akka.http.scaladsl.unmarshalling.Unmarshaller

  //for akka-http routing
  val stringToUserAction: Unmarshaller[String, UserEvent] = Unmarshaller.strict {
    case "click" => UserEvent.Click
    case "impression" => UserEvent.Impression
  }

}

final case class AnalyticsData(timestamp: ZonedDateTime, user_id: Long, event: UserEvent, id: Option[Long] = None)

final case class AnalyticsSummary(unique_users: Long, clicks: Long, impressions: Long) {
  def toHttpResponseString: String =
    s"""|unique_users,$unique_users
       |clicks,$clicks
       |impressions,$impressions""".stripMargin

}

