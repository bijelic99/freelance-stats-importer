package com.freelanceStats.components.lastImportedElementDateTimeProvider

import com.freelanceStats.configurations.ElasticConfiguration
import com.google.inject.ImplementedBy
import com.sksamuel.elastic4s.ElasticApi.search
import com.sksamuel.elastic4s.ElasticDsl.SearchHandler
import com.sksamuel.elastic4s.{
  ElasticClient,
  ElasticProperties,
  RequestFailure,
  RequestSuccess
}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.searches.aggs.MaxAggregation
import com.sksamuel.elastic4s.requests.searches.term.TermQuery
import org.joda.time.DateTime

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ElasticLastImportedElementDateTimeProvider])
trait LastImportedElementDateTimeProvider {
  def getLastImportedElementDateTime(source: String): Future[Option[DateTime]]
}

class ElasticLastImportedElementDateTimeProvider @Inject() (
    elasticConfiguration: ElasticConfiguration
)(implicit ec: ExecutionContext)
    extends LastImportedElementDateTimeProvider {
  private val client: ElasticClient = ElasticClient(
    JavaClient(ElasticProperties(elasticConfiguration.endpoint))
  )

  override def getLastImportedElementDateTime(
      source: String
  ): Future[Option[DateTime]] =
    client
      .execute(
        search(elasticConfiguration.jobIndex)
          .size(0)
          .query(TermQuery("source", source))
          .aggregations(MaxAggregation("createdMax", Some("created")))
      )
      .map {
        case RequestSuccess(_, _, _, result) =>
          result.aggs
            .getAgg("createdMax")
            .get
            .dataAsMap
            .get("value_as_string")
            .map(_.asInstanceOf[String])
            .map(DateTime.parse)
        case RequestFailure(_, _, _, error) =>
          throw new Exception("Error while getting latestJo", error.asException)
      }
}
