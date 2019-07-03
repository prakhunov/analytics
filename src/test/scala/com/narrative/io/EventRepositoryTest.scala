package com.narrative.io

import java.time.temporal.ChronoUnit
import java.time.{ Instant, ZoneOffset, ZonedDateTime }

import akka.actor.{ ActorRef, Props }
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.narrative.io.models.db.EventRepository
import com.narrative.io.models.{ AnalyticsData, AnalyticsSummary, UserEvent }
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }

object TestDataGenerator {
  implicit val dtOrdering: Ordering[ZonedDateTime] = Ordering.by(_.toEpochSecond)

  def getTestData: (List[AnalyticsData], List[(ZonedDateTime, AnalyticsSummary)]) = {
    //generate 10,000 events with a distribution of 70% clicks, and 30% impressions (sounds about right to me)
    val events = Gen.frequency(
      (7, UserEvent.Click),
      (3, UserEvent.Impression))
    val userIds: List[Long] = List(1, 2, 3, 4, 5, 6, 7, 8, 9)
    //fill up the last 8 hours with data..
    val nowHour = ZonedDateTime.now(ZoneOffset.UTC)

    //for the last ten hours created events by the frequency
    //specified above with a timestamp in that hour
    val testData = (for {
      hour <- 10 until 1 by -1 //generate 10 hours of past hour data
      _ <- 1 until 100 //10000 events per hour
      event <- events.sample // generate an event in the 70 to 30 split
      userId <- Gen.oneOf(userIds).sample //pick one of the user ids above
      newTimeStamp = nowHour.minusHours(hour) //the zoneddatetime of that hour
    } yield AnalyticsData(newTimeStamp, userId, event)).toList

    val mySampledTestDataByHour: Map[ZonedDateTime, List[AnalyticsData]] =
      testData.groupBy(_.timestamp.truncatedTo(ChronoUnit.HOURS))

    val summaryByHourSorted: List[(ZonedDateTime, AnalyticsSummary)] = mySampledTestDataByHour.map {
      case (hour, data) =>
        val uniqueUserIds = data.map(_.user_id).distinct.length
        val clicks = data.collect {
          case AnalyticsData(_, _, UserEvent.Click, _) => 1
        }.length
        val impressions = data.collect {
          case AnalyticsData(_, _, UserEvent.Impression, _) => 1
        }.length

        (hour, AnalyticsSummary(uniqueUserIds, clicks, impressions))
    }.toList.sortBy(_._1)

    (testData, summaryByHourSorted)

  }
}

class EventRepositoryTest extends WordSpec with Matchers with ScalaFutures with BeforeAndAfterAll with BeforeAndAfter {
  implicit val executionContext = ExecutionContext.Implicits.global

  override def beforeAll() {
    Await.ready(EventRepository.createSchema(), 90.seconds)
    Await.ready(EventRepository.deleteAllRows(), 90.seconds)
  }

  "Event repository" should {
    "insert and fetch stats correctly" in {

      val (testData, summaryByHourSorted) = TestDataGenerator.getTestData

      whenReady(EventRepository.storeMultiple(testData)) {
        _ =>
          {
            summaryByHourSorted.map {
              case (hour, data) =>
                whenReady(EventRepository.retrieve(hour)) {
                  dbResult =>
                    {
                      dbResult shouldEqual data
                    }
                }
            }
          }
      }

    }
  }

}

class AnalyticsRoutesTest extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
  with AnalyticsRoutes with AnalyticsCache with BeforeAndAfterAll {

  override val analyticsCache = constructCache
  override val analyticsActor: ActorRef =
    system.actorOf(Props(new AnalyticsActor(analyticsCache)), "analyticsActor")

  override def beforeAll() {
    Await.ready(EventRepository.createSchema(), 90.seconds)
    Await.ready(EventRepository.deleteAllRows(), 90.seconds)
  }

  "Routes" should {
    "insert and fetch stats correctly" in {
      val (testData, summaryByHourSorted) = TestDataGenerator.getTestData

      val postRequests = testData.map(ad => {
        val event = if (ad.event == UserEvent.Click) "click" else "impression"
        Post(uri = s"/analytics?timestamp=${ad.timestamp.toEpochSecond}&user=${ad.user_id}&event=$event")
      })

      val getRequests = summaryByHourSorted.map {
        case (zdt, summary) => (HttpRequest(uri = s"/analytics?timestamp=${zdt.toEpochSecond.toString}"), summary)
      }

      postRequests.foreach(request => {
        request ~> analyticsRoutes ~> check {
          status should ===(StatusCodes.NoContent)
        }
      })

      //wait since we the route immediately returns and we don't really know if it has been saved yet

      val beforeQuery = Instant.now

      getRequests.foreach {
        case (request, summary) =>
          request ~> analyticsRoutes ~> check {
            status should ===(StatusCodes.OK)
            entityAs[String] should ===(summary.toHttpResponseString)
          }
      }
      val afterQuery = Instant.now

      val beforeCache = Instant.now
      getRequests.foreach {
        case (request, summary) =>
          request ~> analyticsRoutes ~> check {
            status should ===(StatusCodes.OK)
            entityAs[String] should ===(summary.toHttpResponseString)
          }
      }

      val afterCache = Instant.now

      val cacheTiming = afterCache.toEpochMilli - beforeCache.toEpochMilli
      val queryTiming = afterQuery.toEpochMilli - beforeQuery.toEpochMilli

      /*This is a very incorrect test (unscientific) but the times on the second run should be lower
      since it should be hitting the cache but it's a quick and dry one to make sure the cache is working*/
      cacheTiming should be < queryTiming
    }

  }

}

