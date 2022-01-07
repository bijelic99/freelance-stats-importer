package com.freelanceStats

import buildInfo.BuildInfo
import com.freelanceStats.components.StreamMaintainer
import com.freelanceStats.modules.MainModule
import com.google.inject.Guice

object Main extends App {
  println(s"Starting ${BuildInfo.name} ${BuildInfo.version}")
  val injector = Guice.createInjector(new MainModule)
  val streamMaintainer = injector.getInstance(classOf[StreamMaintainer])
  streamMaintainer.start()
}
