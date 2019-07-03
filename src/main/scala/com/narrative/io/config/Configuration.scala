package com.narrative.io.config

import com.typesafe.config.ConfigFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object Configuration {
  private lazy val config = ConfigFactory.load()
  private lazy val httpConfig = config.getConfig("http")

  lazy val httpHost: String = httpConfig.getString("interface")
  lazy val httpPort: Int = httpConfig.getInt("port")
  lazy val databaseConfig = DatabaseConfig.forConfig[JdbcProfile]("main_db")
}
