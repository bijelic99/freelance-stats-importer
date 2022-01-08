package com.freelanceStats.components

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, RunnableGraph}
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import com.freelanceStats.commons.models.RawJob
import com.freelanceStats.components.dataSourceFactory.DataSourceFactory
import com.freelanceStats.components.jobArchiverFactory.JobArchiverFactory
import com.freelanceStats.components.jobCreatorFactory.JobCreatorFactory
import com.freelanceStats.models.PageMetadata
import com.freelanceStats.queueClient.QueueConsumer

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StreamMaintainer @Inject() (
    dataSourceFactory: DataSourceFactory[PageMetadata],
    jobCreatorFactory: JobCreatorFactory[PageMetadata],
    jobArchiverFactory: JobArchiverFactory,
    queueConsumer: QueueConsumer[RawJob]
)(
    override implicit val executionContext: ExecutionContext,
    override implicit val system: ActorSystem
) extends com.freelanceStats.commons.streamMaintainer.StreamMaintainer {

  override implicit val materializer: Materializer = Materializer.matFromSystem
  override implicit val timeout: FiniteDuration = 1.minute

  private val source = dataSourceFactory.create
  private val jobCreator = jobCreatorFactory.create
  private val jobArchiver = jobArchiverFactory.create
  private val sink = queueConsumer.sink

  override val runnableGraph: RunnableGraph[(KillSwitch, Future[Done])] =
    source
      .viaMat(KillSwitches.single)(Keep.right)
      .via(jobCreator)
      .via(jobArchiver)
      .toMat(sink)(Keep.both)
}
