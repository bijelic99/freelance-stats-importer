package com.freelanceStats.components

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, RunnableGraph}
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import com.freelanceStats.components.dataSource.DataSource
import com.freelanceStats.components.jobArchiver.JobArchiver
import com.freelanceStats.components.queue.QueueClient

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StreamMaintainer @Inject() (
    dataSource: DataSource,
    jobArchiver: JobArchiver,
    queueClient: QueueClient
)(
    override implicit val executionContext: ExecutionContext,
    override implicit val system: ActorSystem
) extends com.freelanceStats.commons.streamMaintainer.StreamMaintainer {

  override implicit val materializer: Materializer = Materializer.matFromSystem
  override implicit val timeout: FiniteDuration = 1.minute

  override val runnableGraph: RunnableGraph[(KillSwitch, Future[Done])] =
    dataSource()
      .viaMat(KillSwitches.single)(Keep.right)
      .via(jobArchiver())
      .toMat(queueClient.sink)(Keep.both)
}
