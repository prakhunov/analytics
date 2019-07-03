package com.narrative.io

import java.time.{ Instant, ZoneOffset, ZonedDateTime }

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging
import akka.pattern.ask
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.util.Timeout
import com.narrative.io.models.{ AnalyticsData, AnalyticsSummary, UserEvent }

import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import AnalyticsActor._

trait AnalyticsRoutes {

  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[AnalyticsRoutes])

  def analyticsActor: ActorRef

  implicit lazy val timeout = Timeout(15.seconds)

  val zonedDateTimeUnmarshall: Unmarshaller[Long, ZonedDateTime] = Unmarshaller.strict {
    epochSeconds =>
      val i = Instant.ofEpochSecond(epochSeconds)
      // from the email we are always going to assume UTC
      ZonedDateTime.ofInstant(i, ZoneOffset.UTC)
  }

  lazy val analyticsRoutes: Route =
    pathPrefix("analytics") {
      get {
        parameters('timestamp.as[Long].as(zonedDateTimeUnmarshall)) { timestamp =>
          //ask the actor for data
          val pendingResult = (analyticsActor ? RequestData(timestamp)).mapTo[AnalyticsSummary]
          onComplete(pendingResult) {
            case Success(r) => complete(r.toHttpResponseString)
            case Failure(ex) =>
              val exceptionMessage = ex.getMessage
              log.error(s"Error getting analytics for the following timestamp : ${timestamp.toString}, Error Received: $exceptionMessage")
              complete(StatusCodes.InternalServerError, s"An error occurred: $exceptionMessage")
          }
        }
      } ~
        post {
          parameters('timestamp.as[Long].as(zonedDateTimeUnmarshall), 'user.as[Long], 'event.as(UserEvent.stringToUserAction)) {
            (timestamp, user_id, event) =>
              {
                val ad = AnalyticsData(timestamp, user_id, event)
                //send the data to the actor but immediately return
                analyticsActor ! StoreEvent(ad)
                complete(StatusCodes.NoContent)
              }
          }
        }
    }

}
