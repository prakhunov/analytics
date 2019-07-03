package com.narrative.io.models.db

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.narrative.io.models.UserEvent.{ Click, Impression }
import com.narrative.io.models.{ AnalyticsData, AnalyticsSummary, UserEvent }
import com.narrative.io.db.DBConnection

import scala.concurrent.{ ExecutionContext, Future }

object EventRepository extends DBConnection {

  import com.narrative.io.db.DB.api._

  private implicit val userEventColumnType = MappedColumnType.base[UserEvent, Int]({
    case Click => 0
    case Impression => 1
  }, {
    case 0 => Click
    case 1 => Impression
  })

  private class EventTable(tag: Tag) extends Table[AnalyticsData](tag, "event_store") {

    /*slick still doesn't support giving default values for UUID's so I'm going to use a long here..
    in fact I stopped using slick myself and had the original pull request that got denied years ago
    but slick is the easiest to setup for this take home project*/

    def id = column[Long]("event_id", O.PrimaryKey, O.AutoInc)
    def timestamp = column[ZonedDateTime]("event_timestamp")
    def user_id = column[Long]("event_user")

    /*we should have a foreign key constraint here for the event_type table to make sure
    but in this quick exercise it's not really needed*/
    def event = column[UserEvent]("event_type")

    def * = (timestamp, user_id, event, id.?) <> (AnalyticsData.tupled, AnalyticsData.unapply)

    //it should use this index for the counting by distinct users
    def timeUserIdx = index("idx_time_user", (timestamp, user_id))
    //it will use this index when you group by event type and count it up
    def timeEventIdx = index("idx_time_event", (timestamp, event))

  }
  private val events = TableQuery[EventTable]

  def deleteAllRows() = db.run { events.delete }

  def createSchema(): Future[Unit] = db.run { events.schema.create }

  def store(a: AnalyticsData): Future[Int] = db.run { (events += a).transactionally }

  def storeMultiple(a: List[AnalyticsData]) = db.run { (events ++= a).transactionally }

  /*Right now it's using a generic relational database, you can optimize this with Spark but for this that's probably a bit
  overkill.

  I would actually use a timeseriesdb like timescaledb (which is postgresql based and can interface with slick without much issue)

  Then I would implement this with two continuous aggregate views
  https://docs.timescale.com/v1.3/using-timescaledb/continuous-aggregates

  I would create one for unique users bucketed by hour, and the event types bucketed by hours as well

  */

  //so the actor can pass the correct executioncontext for this. DBIO run's in their own execution context but
  //combining the result must be in the actor's executioncontext
  def retrieve(epochTime: ZonedDateTime)(implicit ec: ExecutionContext): Future[AnalyticsSummary] = {

    //the sql between is inclusive so don't include the next hour, and we get epoch in seconds so thats the most amount of
    //resolution needed
    val min = epochTime.truncatedTo(ChronoUnit.HOURS)
    val max = min.plusHours(1).minusSeconds(1)

    // thankfully slick isn't that bad but in most ORMS this can cause a problem (since slick is technically not an ORM)
    val base = events.filter(_.timestamp.between(min, max))
    val distinctByUser = base.map(_.user_id).distinct.length
    val dataQuery = base
      .groupBy(_.event)
      .map {
        case (event, rest) => (event, distinctByUser, rest.length)
      }
    /*
    The above code looks confusing but it transforms into the following query:

    If you are dealing with a bad SQL Server this might run the count of distinct users for each type
    which if that is the case you can fix this by making two calls to your SQL server
    But most SQL engines should be able to optimize this fairly well

SELECT "event_type",
       (SELECT Count(*)
        FROM   (SELECT DISTINCT "event_user"
                FROM   "event_store"
                WHERE  "event_timestamp" BETWEEN
                       '1970-01-01T00:00Z' AND '1970-01-01T00:59:59Z'
               )),
       Count(*)
FROM   "event_store"
WHERE  "event_timestamp" BETWEEN '1970-01-01T00:00Z' AND '1970-01-01T00:59:59Z'
GROUP  BY "event_type"

     */

    db.run(dataQuery.result).map {
      result =>
        result.foldLeft(AnalyticsSummary(0, 0, 0)) {
          case (agg, (UserEvent.Click, distinctUsers, numClicks)) =>
            agg.copy(unique_users = distinctUsers, clicks = numClicks)
          case (agg, (UserEvent.Impression, distinctUsers, numImpressions)) =>
            agg.copy(unique_users = distinctUsers, impressions = numImpressions)
        }
    }
  }

}
