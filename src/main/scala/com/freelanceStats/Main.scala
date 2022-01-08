package com.freelanceStats

import buildInfo.BuildInfo
import com.freelanceStats.commons.modules.ModuleLoader
import com.freelanceStats.components.StreamMaintainer
import com.google.inject.Guice
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object Main extends App {
  val log = LoggerFactory.getLogger(getClass)
  log.info(s"Starting ${BuildInfo.name} ${BuildInfo.version}")
  val injector = Guice.createInjector(new ModuleLoader)
  val streamMaintainer = injector.getInstance(classOf[StreamMaintainer])
  streamMaintainer.start()

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      log.info("Shutting down the application")
      Await.result(streamMaintainer.stop(), 10.seconds)
      Thread.sleep(1000 * 2)
    }
  })
}
