package com.narrative.io.db

import com.narrative.io.config.Configuration

//a singleton so the application has one instance to the database with it's one connection pool

object DB {
  lazy val db = Configuration.databaseConfig.db
  lazy val api = Configuration.databaseConfig.profile.api
}

trait DBConnection {
  lazy val db = DB.db
}
