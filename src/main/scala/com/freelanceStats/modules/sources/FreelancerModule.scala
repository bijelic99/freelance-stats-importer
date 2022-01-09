package com.freelanceStats.modules.sources

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.freelanceStats.components.S3Client
import com.freelanceStats.components.dataSourceFactory.{
  DataSourceFactory,
  FreelancerDataSourceFactory
}
import com.freelanceStats.components.jobArchiverFactory.{
  DateBasedJobArchiverFactory,
  JobArchiverFactory
}
import com.freelanceStats.components.jobCreatorFactory.{
  FreelancerJobCreatorFactory,
  JobCreatorFactory
}
import com.freelanceStats.configurations.ApplicationConfiguration
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

  @Provides
  @Singleton
  def jobCreatorFactoryProvider(
      applicationConfiguration: ApplicationConfiguration
  )(implicit
      materializer: Materializer
  ): JobCreatorFactory[PageMetadata] =
    new FreelancerJobCreatorFactory(applicationConfiguration)
      .asInstanceOf[JobCreatorFactory[PageMetadata]]

  @Provides
  @Singleton
  def jobArchiverFactoryProvider(
      s3Client: S3Client,
      applicationConfiguration: ApplicationConfiguration
  )(implicit
      materializer: Materializer
  ): JobArchiverFactory =
    new DateBasedJobArchiverFactory(s3Client, applicationConfiguration)
}
