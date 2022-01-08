package com.freelanceStats.modules

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.freelanceStats.components.S3Client
import com.freelanceStats.components.dataSourceFactory.{
  DataSourceFactory,
  FreelancerDataSourceFactory
}
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import com.freelanceStats.models.pageMetadata.PageMetadata
import com.google.inject.{AbstractModule, Provides}

import javax.inject.Singleton
import scala.concurrent.ExecutionContext

class FreelancerModule extends AbstractModule {
  @Provides
  @Singleton
  def dataSourceFactoryProvider(
      s3Client: S3Client,
      configuration: FreelancerSourceConfiguration
  )(implicit
      actorSystem: ActorSystem,
      executionContext: ExecutionContext,
      materializer: Materializer
  ): DataSourceFactory[PageMetadata] =
    new FreelancerDataSourceFactory(
      s3Client,
      configuration
    ).asInstanceOf[DataSourceFactory[PageMetadata]]
}
