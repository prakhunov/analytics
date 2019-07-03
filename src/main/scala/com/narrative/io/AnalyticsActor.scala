package com.narrative.io

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ Actor, ActorLogging }
import akka.http.caching.scaladsl.Cache
import akka.util.Timeout
import com.narrative.io.models.db.EventRepository
import com.narrative.io.models.{ AnalyticsData, AnalyticsSummary }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object AnalyticsActor {
  final case class RequestData(timestamp: ZonedDateTime)
  final case class StoreEvent(eventData: AnalyticsData)
}

class AnalyticsActor(cache: Cache[ZonedDateTime, AnalyticsSummary]) extends Actor with ActorLogging {
  import AnalyticsActor._
  import akka.pattern.pipe
  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout: Timeout = 5.seconds

  override def receive: Receive = {
    case StoreEvent(eventData: AnalyticsData) =>
      EventRepository.store(eventData)
        .onComplete {
          case Success(_) => log.debug(s"Stored Event: $eventData")
          case Failure(exception) =>
            log.error(s"Error: Storing Event: $eventData : Due To: ${exception.getMessage}")
        }
    case RequestData(timestamp: ZonedDateTime) =>
      val hourBucket = timestamp.truncatedTo(ChronoUnit.HOURS)
      //fetch from cache if there, if not compute it again and send it back to the sender
      cache.getOrLoad(hourBucket, EventRepository.retrieve).pipeTo(sender())
  }

}
