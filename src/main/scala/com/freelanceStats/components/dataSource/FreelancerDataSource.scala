package com.freelanceStats.components.dataSource

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.stream.scaladsl.{
  Broadcast,
  Flow,
  GraphDSL,
  Merge,
  Source,
  StreamConverters,
  Unzip,
  Zip
}
import akka.stream.{Materializer, SourceShape}
import com.freelanceStats.commons.models.UnsavedRawJob
import com.freelanceStats.components.S3Client
import com.freelanceStats.components.dataSource.FreelancerDataSource.{
  ActiveProjectsFetchResults,
  JobIdentifier,
  LastFreelancerProgressMetadata,
  ResultListEmpty
}
import com.freelanceStats.configurations.ApplicationConfiguration
import com.freelanceStats.configurations.sources.FreelancerSourceConfiguration
import com.freelanceStats.models.pageMetadata.FreelancerProgressMetadata
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class FreelancerDataSource @Inject() (
    override val s3Client: S3Client,
    override val configuration: FreelancerSourceConfiguration,
    applicationConfiguration: ApplicationConfiguration
)(
    implicit val actorSystem: ActorSystem,
    override implicit val executionContext: ExecutionContext,
    override implicit val materializer: Materializer
) extends DataSource[FreelancerProgressMetadata] {
  import FreelancerProgressMetadata._

  override val name: String = "freelancer-data-source"

  override implicit val log: LoggingAdapter =
    Logging(materializer.system, getClass)

  protected override def defaultPageMetadata: FreelancerProgressMetadata =
    FreelancerProgressMetadata.apply(
      fetchFrom = DateTime.now(),
      fetchTo = DateTime.now()
    )

  override def apply(): Source[UnsavedRawJob, _] =
    jobIdentifierSource
      .flatMapConcat { jobIdentifierSource =>
        jobIdentifierSource
          .via(fetchJobFlow)
      }

  private lazy val jobIdentifierSource: Source[Source[JobIdentifier, _], _] =
    Source.fromGraph {
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val firstProgressMetadataSource = builder.add(firstProgressMetadata())
        val fetchProjectIdentifiersFlow =
          builder.add(getActiveProjectsPageMetadata)

        val _nextJobFetchParameterFlow = builder.add(nextJobFetchParameterFlow)

        val saveProgressMetadataSink = builder.add(
          Flow[(ResultListEmpty, LastFreelancerProgressMetadata)]
            .map(_._2)
            .to(saveProgressMetadata())
        )

        val throttle = builder.add(
          Flow[(ResultListEmpty, LastFreelancerProgressMetadata)]
            .throttle(1, configuration.frequency)
        )

        val progressMetadataMerge =
          builder.add(Merge[FreelancerProgressMetadata](2))

        val metadataBroadcast =
          builder.add(Broadcast[FreelancerProgressMetadata](2))

        val metadataUnzip = builder.add(
          Unzip[Source[JobIdentifier, _], ResultListEmpty]()
        )

        val metadataZip =
          builder.add(Zip[ResultListEmpty, LastFreelancerProgressMetadata])

        val zippedMetadataBroadcast = builder.add(
          Broadcast[(ResultListEmpty, LastFreelancerProgressMetadata)](2)
        )

        firstProgressMetadataSource.out ~> progressMetadataMerge.in(0)

        progressMetadataMerge.out ~> metadataBroadcast.in

        metadataBroadcast.out(0) ~> fetchProjectIdentifiersFlow.in

        fetchProjectIdentifiersFlow.out ~> metadataUnzip.in

        metadataUnzip.out1 ~> metadataZip.in0

        metadataBroadcast ~> metadataZip.in1

        metadataZip.out ~> zippedMetadataBroadcast.in

        zippedMetadataBroadcast.out(0) ~> throttle.in

        zippedMetadataBroadcast.out(1) ~> saveProgressMetadataSink.in

        throttle.out ~> _nextJobFetchParameterFlow.in

        _nextJobFetchParameterFlow.out ~> progressMetadataMerge.in(1)

        SourceShape(metadataUnzip.out0)
      }
    }

  private lazy val activeProjectsFetchPool =
    Http().superPool[FreelancerProgressMetadata]()

  private lazy val getActiveProjectsPageMetadata
      : Flow[FreelancerProgressMetadata, ActiveProjectsFetchResults, _] =
    Flow[FreelancerProgressMetadata]
      .log(
        name,
        { metadata =>
          s"Fetching job id's for '$metadata'"
        }
      )
      .map { metadata =>
        val fetchFromSeconds =
          TimeUnit.MILLISECONDS.toSeconds(metadata.fetchFrom.getMillis)
        val fetchToSeconds =
          TimeUnit.MILLISECONDS.toSeconds(metadata.fetchTo.getMillis)
        val params = Seq(
          "compact=true",
          s"limit=${configuration.resultsPerPageLimit}",
          s"offset=${metadata.offset}",
          s"from_time=$fetchFromSeconds",
          s"to_time=$fetchToSeconds"
        ).mkString("?", "&", "")
        HttpRequest(
          method = HttpMethods.GET,
          uri = s"${configuration.url}/api/projects/0.1/projects/active$params"
        ) -> metadata
      }
      .via(activeProjectsFetchPool)
      .map {
        case (Success(response), _) if response.status.isSuccess() =>
          val body = Json.parse(
            response.entity.dataBytes.runWith(StreamConverters.asInputStream())
          )
          val projects =
            (body \ "result" \ "projects")
              .as[Seq[JsObject]]
          val identifiers = projects
            .map(_.\("id").as[Long].toString)
          Source(identifiers) -> identifiers.isEmpty
        case (Success(response), metadata) =>
          val message =
            s"Received response with code ${response.status} for '$metadata'"
          log.error(message)
          throw new Exception(message)
        case (Failure(throwable), metadata) =>
          val message = s"Failed to receive response for '$metadata'"
          log.error(message, throwable)
          throw new Exception(message, throwable)
      }
      .log(
        name,
        { case (_, resultListEmpty) =>
          s"Fetched jobs, resultListEmpty = $resultListEmpty"
        }
      )

  private lazy val nextJobFetchParameterFlow: Flow[
    (ResultListEmpty, LastFreelancerProgressMetadata),
    FreelancerProgressMetadata,
    NotUsed
  ] =
    Flow[(ResultListEmpty, LastFreelancerProgressMetadata)]
      .map {
        case (true, lastMetadata) =>
          lastMetadata
            .createNextProgressMetadata(configuration.maxFetchOffset)
        case (false, lastMetadata) =>
          lastMetadata
            .incrementResultOffset(configuration.resultsPerPageLimit)
      }
      .log(
        name,
        { metadata =>
          s"Created next progressMetadata: '$metadata'"
        }
      )

  private lazy val jobFetchPool = Http().superPool[JobIdentifier]()

  private lazy val fetchJobFlow: Flow[JobIdentifier, UnsavedRawJob, NotUsed] =
    Flow[JobIdentifier]
      .log(
        name,
        { jobIdentifier =>
          s"Fetching job with id: '$jobIdentifier'"
        }
      )
      .map { identifier =>
        val params = Seq(
          "compact=true",
          "full_description=true",
          "job_details=true",
          "qualification_details=true",
          "user_details=true",
          "location_details=true",
          "user_country_details=true",
          "user_location_details=true"
        ).mkString("?", "&", "")
        HttpRequest(
          method = HttpMethods.GET,
          uri =
            s"${configuration.url}/api/projects/0.1/projects/$identifier$params"
        ) -> identifier
      }
      .via(jobFetchPool)
      .map {
        case (Success(response), identifier) if response.status.isSuccess() =>
          lazy val byteArray =
            response.entity.dataBytes
              .runWith(StreamConverters.asInputStream())
              .readAllBytes()
          UnsavedRawJob(
            sourceId = identifier,
            source = applicationConfiguration.source,
            data = StreamConverters.fromInputStream(() =>
              new ByteArrayInputStream(byteArray)
            ),
            contentType = response.entity.contentType.toString(),
            contentSize =
              response.entity.contentLengthOption.getOrElse(byteArray.length)
          )
        case (Success(response), identifier) =>
          val message =
            s"Received response with code ${response.status} for job with id: '$identifier'"
          log.error(message)
          throw new Exception(message)
        case (Failure(throwable), identifier) =>
          val message =
            s"Failed to receive response for job with id: '$identifier'"
          log.error(message, throwable)
          throw new Exception(message, throwable)
      }
      .log(
        name,
        { case UnsavedRawJob(sourceId, _, _, _, _) =>
          s"Fetched job with id: '$sourceId'"
        }
      )
}

object FreelancerDataSource {
  type JobIdentifier = String
  type ResultListEmpty = Boolean
  type LastFreelancerProgressMetadata = FreelancerProgressMetadata
  type ActiveProjectsFetchResults = (Source[JobIdentifier, _], ResultListEmpty)
}
