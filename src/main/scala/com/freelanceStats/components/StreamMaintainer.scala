package com.freelanceStats.components

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, RunnableGraph}
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import com.freelanceStats.components.dataSourceFactory.DataSourceFactory
import com.freelanceStats.components.jobArchiverFactory.JobArchiverFactory
import com.freelanceStats.components.queue.QueueClient
import com.freelanceStats.models.pageMetadata.ProgressMetadata

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StreamMaintainer @Inject() (
    dataSourceFactory: DataSourceFactory[ProgressMetadata],
    jobArchiverFactory: JobArchiverFactory,
    queueClient: QueueClient
)(
    override implicit val executionContext: ExecutionContext,
    override implicit val system: ActorSystem
) extends com.freelanceStats.commons.streamMaintainer.StreamMaintainer {

  override implicit val materializer: Materializer = Materializer.matFromSystem
  override implicit val timeout: FiniteDuration = 1.minute

  private val source = dataSourceFactory.create
  private val jobArchiver = jobArchiverFactory.create
  private val sink = queueClient.sink

  override val runnableGraph: RunnableGraph[(KillSwitch, Future[Done])] =
    source
      .viaMat(KillSwitches.single)(Keep.right)
      .via(jobArchiver)
      .toMat(sink)(Keep.both)
}
