package com.squamed.core.search

import com.squamed.core.encounter.domain.Encounter
import com.squamed.core.encounter.repository.EncounterRepository
import com.squamed.core.patient.domain.PatientRecord
import com.squamed.core.patient.repository.PatientRecordRepository
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.mapping.TypeMapping
import org.opensearch.client.opensearch.core.DeleteRequest
import org.opensearch.client.opensearch.core.IndexRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SearchIndexer(
  private val client: OpenSearchClient,
  private val patientRecordRepository: PatientRecordRepository,
  private val encounterRepository: EncounterRepository,
  @Value("\${squamed.search.reindex-on-startup:true}") private val reindexOnStartup: Boolean,
) {
  companion object {
    const val INDEX_PATIENTS = "squamed-patients"
    const val INDEX_ENCOUNTERS = "squamed-encounters"
  }

  private val logger = LoggerFactory.getLogger(SearchIndexer::class.java)

  @Volatile
  private var ensured = false

  @EventListener(ApplicationReadyEvent::class)
  fun onApplicationReady() {
    ensureIndices()
    if (reindexOnStartup) {
      backfill()
    }
  }

  fun backfill() {
    try {
      val patients = patientRecordRepository.findAll()
      patients.forEach { indexPatient(it) }
      val encounters = encounterRepository.findAll()
      encounters.forEach { indexEncounter(it) }
      logger.info("OpenSearch backfill complete: {} patients, {} encounters", patients.size, encounters.size)
    } catch (e: Exception) {
      logger.warn("OpenSearch backfill failed: {}", e.message)
    }
  }

  fun ensureIndices() {
    if (ensured) return
    synchronized(this) {
      if (ensured) return
      try {
        createIndexIfMissing(
          INDEX_PATIENTS,
          TypeMapping.of { m ->
            m
              .properties("mrn") { it.keyword { it } }
              .properties("firstName") { it.text { it } }
              .properties("lastName") { it.text { it } }
              .properties("phone") { it.keyword { it } }
              .properties("qid") { it.keyword { it } }
              .properties("dateOfBirth") { it.date { it } }
              .properties("gender") { it.keyword { it } }
          },
        )
        createIndexIfMissing(
          INDEX_ENCOUNTERS,
          TypeMapping.of { m ->
            m
              .properties("patientId") { it.keyword { it } }
              .properties("encounterType") { it.keyword { it } }
              .properties("status") { it.keyword { it } }
              .properties("startedAt") { it.date { it } }
              .properties("reason") { it.text { it } }
              .properties("chiefComplaint") { it.text { it } }
          },
        )
        ensured = true
      } catch (e: Exception) {
        logger.warn("Failed to ensure OpenSearch indices: {}", e.message)
      }
    }
  }

  fun indexPatient(patient: PatientRecord) {
    try {
      ensureIndices()
      val id = patient.id ?: return
      val doc =
        mapOf(
          "id" to id.toString(),
          "mrn" to patient.mrn,
          "hospitalId" to patient.hospitalId?.toString(),
          "firstName" to patient.firstName.lowercase(),
          "lastName" to patient.lastName.lowercase(),
          "fullName" to "${patient.firstName} ${patient.lastName}".trim(),
          "dateOfBirth" to patient.dateOfBirth.toString(),
          "gender" to patient.gender,
          "phone" to patient.phone,
          "qid" to patient.qid,
          "email" to patient.email,
          "nationality" to patient.nationality,
          "city" to patient.city,
        )
      val request =
        IndexRequest
          .Builder<Map<String, Any?>>()
          .index(INDEX_PATIENTS)
          .id(id.toString())
          .document(doc)
          .build()
      client.index(request)
    } catch (e: Exception) {
      logger.warn("Failed to index patient {}: {}", patient.id, e.message)
    }
  }

  fun deletePatient(id: UUID) {
    try {
      ensureIndices()
      val request =
        DeleteRequest
          .Builder()
          .index(INDEX_PATIENTS)
          .id(id.toString())
          .build()
      client.delete(request)
    } catch (e: Exception) {
      logger.warn("Failed to delete patient {}: {}", id, e.message)
    }
  }

  fun indexEncounter(encounter: Encounter) {
    try {
      ensureIndices()
      val id = encounter.id ?: return
      val doc =
        mapOf(
          "id" to id.toString(),
          "patientId" to encounter.patientId.toString(),
          "hospitalId" to encounter.hospitalId?.toString(),
          "encounterType" to encounter.encounterType,
          "status" to encounter.status,
          "reason" to encounter.reason,
          "chiefComplaint" to encounter.chiefComplaint,
          "startedAt" to encounter.startedAt?.toString(),
          "endedAt" to encounter.endedAt?.toString(),
          "departmentId" to encounter.departmentId?.toString(),
        )
      val request =
        IndexRequest
          .Builder<Map<String, Any?>>()
          .index(INDEX_ENCOUNTERS)
          .id(id.toString())
          .document(doc)
          .build()
      client.index(request)
    } catch (e: Exception) {
      logger.warn("Failed to index encounter {}: {}", encounter.id, e.message)
    }
  }

  fun deleteEncounter(id: UUID) {
    try {
      ensureIndices()
      val request =
        DeleteRequest
          .Builder()
          .index(INDEX_ENCOUNTERS)
          .id(id.toString())
          .build()
      client.delete(request)
    } catch (e: Exception) {
      logger.warn("Failed to delete encounter {}: {}", id, e.message)
    }
  }

  private fun createIndexIfMissing(
    name: String,
    mapping: TypeMapping,
  ) {
    val exists = client.indices().exists { it.index(name) }
    if (exists.value()) {
      return
    }
    client.indices().create { it.index(name).mappings(mapping) }
  }
}
