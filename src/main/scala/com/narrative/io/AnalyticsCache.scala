package com.narrative.io

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.Cache
import com.narrative.io.models.AnalyticsSummary

trait AnalyticsCache {

  def constructCache(implicit actorSystem: ActorSystem): Cache[ZonedDateTime, AnalyticsSummary] = {
    LfuCache[ZonedDateTime, AnalyticsSummary]
  }

  def analyticsCache: Cache[ZonedDateTime, AnalyticsSummary]

}

