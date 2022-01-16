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
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import com.freelanceStats.models.pageMetadata.ProgressMetadata
import com.google.inject.{AbstractModule, Provides}

import javax.inject.Singleton
import scala.concurrent.ExecutionContext

class FreelancerModule extends AbstractModule {
  @Provides
  @Singleton
  def dataSourceFactoryProvider(
      s3Client: S3Client,
      configuration: FreelancerSourceConfiguration,
      applicationConfiguration: ApplicationConfiguration
  )(implicit
      actorSystem: ActorSystem,
      executionContext: ExecutionContext,
      materializer: Materializer
  ): DataSourceFactory[ProgressMetadata] =
    new FreelancerDataSourceFactory(
      s3Client,
      configuration,
      applicationConfiguration
    ).asInstanceOf[DataSourceFactory[ProgressMetadata]]

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
