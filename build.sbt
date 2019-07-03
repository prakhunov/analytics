lazy val akkaHttpVersion = "10.1.8"
lazy val akkaVersion    = "2.5.23"
lazy val slickVersion = "3.3.1"
lazy val sparkVersion = "2.4.2"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.narrative.io",
      scalaVersion    := "2.12.8"
    )),
    name := "analytics",
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-caching" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,

      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.h2database" % "h2" % "1.4.199",
      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.5"         % Test,
      //i'm going to use scalacheck to create lots of random data for this..
      "org.scalacheck" %% "scalacheck" % "1.14.0" % Test

    )
  )
