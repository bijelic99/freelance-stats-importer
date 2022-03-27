package com.freelanceStats.modules.sources

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.freelanceStats.components.S3Client
import com.freelanceStats.components.dataSource.{
  DataSource,
  FreelancerDataSource
}
import com.freelanceStats.components.jobArchiver.{
  DateBasedJobArchiver,
  JobArchiver
}
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import com.google.inject.{AbstractModule, Provides}

import javax.inject.Singleton
import scala.concurrent.ExecutionContext

class FreelancerModule extends AbstractModule {
  @Provides
  @Singleton
  def dataSourceProvider(
      configuration: FreelancerSourceConfiguration,
      applicationConfiguration: ApplicationConfiguration
  )(implicit
      actorSystem: ActorSystem,
      executionContext: ExecutionContext,
      materializer: Materializer
  ): DataSource =
    new FreelancerDataSource(
      configuration,
      applicationConfiguration
    )

  @Provides
  @Singleton
  def jobArchiverProvider(
      s3Client: S3Client,
      applicationConfiguration: ApplicationConfiguration
  )(implicit
      materializer: Materializer
  ): JobArchiver =
    new DateBasedJobArchiver(s3Client, applicationConfiguration)
}
