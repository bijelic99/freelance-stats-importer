package com.freelanceStats.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.freelanceStats.components.lastImportedElementDateTimeProvider.LastImportedElementDateTimeProvider
import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration
import scala.util.chaining._
import scala.util.{Failure, Success}

object PeriodProvider {
  case class Props(
      interval: FiniteDuration,
      lastImportedElementDateTimeProvider: LastImportedElementDateTimeProvider,
      source: String
  )
  sealed trait StreamCommand
  case class Period(from: DateTime, to: DateTime) extends StreamCommand

  sealed trait Command
  case object Ack extends Command
  case class Initialize(sourceActorRef: ActorRef[StreamCommand]) extends Command
  case class UnexpectedError(throwable: Throwable)
      extends Command
      with StreamCommand
  case class SetLastDateTimeIfAvailable(dateTimeOpt: Option[DateTime])
      extends Command

  def apply(props: Props): Behavior[Command] = uninitialized(props)

  def uninitialized(props: Props): Behavior[Command] = Behaviors.setup {
    implicit context =>
      Behaviors.receiveMessagePartial { case Initialize(sourceActorRef) =>
        context.log.trace("Getting last imported element dateTime")
        context.pipeToSelf(
          props.lastImportedElementDateTimeProvider
            .getLastImportedElementDateTime(props.source)
        ) {
          case Failure(exception) =>
            UnexpectedError(exception)
          case Success(dateTimeOpt) =>
            SetLastDateTimeIfAvailable(dateTimeOpt)
        }
        initializing(props, sourceActorRef)
      }
  }

  def initializing(props: Props, sourceActorRef: ActorRef[StreamCommand])(
      implicit context: ActorContext[Command]
  ): Behavior[Command] = Behaviors.receiveMessagePartial {
    case SetLastDateTimeIfAvailable(dateTimeOpt) =>
      val dateTime = dateTimeOpt.fold {
        val dt = new DateTime(0)
        context.log.warn(s"Starting from '${dt.toString}'")
        dt
      } { dt =>
        context.log.trace(s"Starting from '${dt.toString}'")
        dt
      }
      context.self ! Ack
      running(props, sourceActorRef, dateTime)
    case error: UnexpectedError =>
      context.log.error(
        "Unexpected error while initializing",
        error.throwable
      )
      sourceActorRef ! error
      Behaviors.stopped
  }

  def running(
      props: Props,
      sourceActorRef: ActorRef[StreamCommand],
      lastDateTime: DateTime
  )(implicit
      context: ActorContext[Command]
  ): Behavior[Command] = Behaviors.receiveMessagePartial { case Ack =>
    context.log.trace("Received ack")
    new DateTime(lastDateTime.getMillis + props.interval.toMillis).pipe {
      case dateTimeTo if !dateTimeTo.isBeforeNow =>
        context.log.info("Waiting before sending new interval")
        context.setReceiveTimeout(props.interval, Ack)
        Behaviors.same
      case dateTimeTo =>
        context.cancelReceiveTimeout()
        val period = Period(lastDateTime, dateTimeTo)
        context.log.info(s"Sending the following period: '$period'")
        sourceActorRef ! period
        running(props, sourceActorRef, dateTimeTo)
    }
  }
}
