package com.freelanceStats.components

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

@Singleton
class StreamMaintainer @Inject() (
)(
    override implicit val executionContext: ExecutionContext,
    override implicit val system: ActorSystem
) extends com.freelanceStats.commons.streamMaintainer.StreamMaintainer {

  override implicit val materializer: Materializer = Materializer.matFromSystem
  override implicit val timeout: FiniteDuration = 1.minute

  override def runnableGraph: RunnableGraph[(KillSwitch, Future[Done])] =
    Source
      .repeat(1)
      .viaMat(KillSwitches.single)(Keep.right)
      .throttle(2, 10.seconds)
      .toMat(Sink.foreach(println))(Keep.both)
}
