package com.freelanceStats.modules

import com.freelanceStats.commons.modules.{
  ActorSystemModule,
  ExecutionContextModule
}
import com.google.inject.AbstractModule

class MainModule extends AbstractModule {
  override def configure(): Unit = {
    install(new ExecutionContextModule)
    install(new ActorSystemModule)
  }
}
