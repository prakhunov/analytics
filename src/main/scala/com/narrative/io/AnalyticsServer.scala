package com.narrative.io

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.narrative.io.config.Configuration
import com.narrative.io.models.db.EventRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{ Failure, Success }

object AnalyticsServer extends App with AnalyticsCache with AnalyticsRoutes {
  implicit val system: ActorSystem = ActorSystem("analyticsServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  lazy val analyticsCache = constructCache

  val analyticsActor = system.actorOf(Props(new AnalyticsActor(analyticsCache)), "analyticsActor")
  lazy val routes: Route = analyticsRoutes
  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, Configuration.httpHost, Configuration.httpPort)

  EventRepository.createSchema().onComplete {
    case Success(_) =>
      serverBinding.onComplete {
        case Success(bound) =>
          println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
        case Failure(e) =>
          Console.err.println(s"Server could not start!")
          e.printStackTrace()
          system.terminate()
      }
    case Failure(e) =>
      Console.err.println("Couldn't start server due to not being able to create the schema on the database")
      e.printStackTrace()
      system.terminate()
  }

}
