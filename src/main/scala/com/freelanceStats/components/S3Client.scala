package com.freelanceStats.components

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.freelanceStats.configurations.S3ClientConfiguration
import com.freelanceStats.s3Client.AkkaStreamsExtension

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class S3Client @Inject() (
    override val configuration: S3ClientConfiguration
)(
    override implicit val executionContext: ExecutionContext,
    override implicit val actorSystem: ActorSystem,
    override implicit val materializer: Materializer
) extends com.freelancerStats.amazonAsyncS3Client.AmazonAsyncS3Client
    with AkkaStreamsExtension {}
