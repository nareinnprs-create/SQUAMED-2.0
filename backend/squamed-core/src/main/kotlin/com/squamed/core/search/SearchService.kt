package com.squamed.core.search

import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.core.search.Hit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SearchService(
  private val client: OpenSearchClient,
) {
  private val logger = LoggerFactory.getLogger(SearchService::class.java)

  private val searchFields =
    listOf(
      "mrn",
      "firstName",
      "lastName",
      "phone",
      "qid",
      "fullName",
      "reason",
      "chiefComplaint",
    )

  fun search(
    q: String,
    type: String? = null,
    from: Int = 0,
    size: Int = 20,
    hospitalId: UUID? = null,
  ): List<SearchHitView> {
    val query = q.trim()
    if (query.isEmpty()) {
      return emptyList()
    }
    val indices =
      when (type?.lowercase()) {
        "patient", "patients" -> listOf(SearchIndexer.INDEX_PATIENTS)
        "encounter", "encounters" -> listOf(SearchIndexer.INDEX_ENCOUNTERS)
        else -> listOf(SearchIndexer.INDEX_PATIENTS, SearchIndexer.INDEX_ENCOUNTERS)
      }
    return try {
      val request =
        SearchRequest.of { req ->
          req
            .index(indices)
            .from(from)
            .size(size)
            .query(
              Query.of { qq ->
                qq.bool { b ->
                  b.must { m -> m.queryString { qs -> qs.query(query).fields(searchFields) } }
                  // 00.04 tenant sweep: OpenSearch sits outside any future Postgres RLS — tenant-filter explicitly.
                  // Trailing `b`: a `?.let` last expression yields null for hid=null and the Java client NPEs on build().
                  if (hospitalId != null) {
                    b.filter { f -> f.term { t -> t.field("hospitalId.keyword").value(FieldValue.of(hospitalId.toString())) } }
                  }
                  b
                }
              },
            )
        }
      val response = client.search(request, Map::class.java)
      response.hits().hits().map { hit -> toView(hit) }
    } catch (e: Exception) {
      logger.warn("Search failed for query [{}]: {}", query, e.toString(), e)
      emptyList()
    }
  }

  fun indexCounts(hospitalId: UUID? = null): Map<String, Long> =
    mapOf(
      SearchIndexer.INDEX_PATIENTS to countIndex(SearchIndexer.INDEX_PATIENTS, hospitalId),
      SearchIndexer.INDEX_ENCOUNTERS to countIndex(SearchIndexer.INDEX_ENCOUNTERS, hospitalId),
    )

  private fun countIndex(
    name: String,
    hospitalId: UUID?,
  ): Long =
    try {
      client
        .count { req ->
          req.index(name)
          if (hospitalId != null) {
            req.query { q -> q.term { t -> t.field("hospitalId.keyword").value(FieldValue.of(hospitalId.toString())) } }
          }
          req
        }.count()
    } catch (e: Exception) {
      logger.warn("Count failed for index [{}]: {}", name, e.message)
      0L
    }

  private fun toView(hit: Hit<Map<*, *>>): SearchHitView {
    val index = hit.index() ?: ""
    val id = hit.id() ?: ""
    val rawSource = hit.source() ?: emptyMap<Any?, Any?>()
    val source = rawSource.mapNotNull { (k, v) -> k?.let { it.toString() to v } }.toMap()
    val isPatient = index == SearchIndexer.INDEX_PATIENTS
    val title: String
    val subtitle: String?
    if (isPatient) {
      title = source["fullName"] as? String ?: ""
      val mrn = source["mrn"] as? String
      val gender = source["gender"] as? String
      val dob = source["dateOfBirth"] as? String
      subtitle = listOfNotNull(mrn, gender, dob).joinToString(", ").takeIf { it.isNotBlank() }
    } else {
      val encounterType = source["encounterType"] as? String ?: ""
      val status = source["status"] as? String ?: ""
      title = "$encounterType#$status"
      subtitle = (source["reason"] as? String) ?: (source["chiefComplaint"] as? String)
    }
    return SearchHitView(
      index = index,
      id = id,
      type = if (isPatient) "patient" else "encounter",
      title = title,
      subtitle = subtitle,
      score = hit.score(),
      fields = source,
    )
  }
}
