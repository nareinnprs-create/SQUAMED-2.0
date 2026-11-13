package com.squamed.core.search

import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.core.search.Hit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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
        SearchRequest.of { it ->
          it
            .index(indices)
            .from(from)
            .size(size)
            .query(Query.of { qq -> qq.queryString { qs -> qs.query(query).fields(searchFields) } })
        }
      val response = client.search(request, Map::class.java)
      response.hits().hits().map { hit -> toView(hit) }
    } catch (e: Exception) {
      logger.warn("Search failed for query [{}]: {}", query, e.message)
      emptyList()
    }
  }

  fun indexCounts(): Map<String, Long> =
    mapOf(
      SearchIndexer.INDEX_PATIENTS to countIndex(SearchIndexer.INDEX_PATIENTS),
      SearchIndexer.INDEX_ENCOUNTERS to countIndex(SearchIndexer.INDEX_ENCOUNTERS),
    )

  private fun countIndex(name: String): Long =
    try {
      client.count { it.index(name) }.count()
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
