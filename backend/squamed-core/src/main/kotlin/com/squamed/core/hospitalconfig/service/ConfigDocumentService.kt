package com.squamed.core.hospitalconfig.service

import com.fasterxml.jackson.databind.JsonNode
import com.squamed.core.events.DomainEvent
import com.squamed.core.events.DomainEventPublisher
import com.squamed.core.events.SchemaRegistry
import com.squamed.core.hospitalconfig.domain.ConfigDocument
import com.squamed.core.hospitalconfig.repository.ConfigDocumentRepository
import com.squamed.core.hospitalconfig.repository.HospitalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ConfigDocumentService(
  private val configDocumentRepository: ConfigDocumentRepository,
  private val hospitalRepository: HospitalRepository,
  private val schemaRegistry: SchemaRegistry,
  private val domainEventPublisher: DomainEventPublisher,
) {
  companion object {
    /** The six config document types shipped by 00.05 (chunk-00 §5) + task-sla (00.11) + dnd-hours (00.12). */
    val TYPES: Set<String> = setOf("working-hours", "holidays", "shifts", "workflow", "queue", "approval", "task-sla", "dnd-hours")
  }

  /**
   * Publishes the next version of `type` for the hospital: content must validate against the
   * per-type JSON Schema on the classpath, previous actives are superseded, and `config.changed`
   * is published. IllegalArgumentException maps to 400 via GlobalExceptionHandler.
   */
  @Transactional
  fun publish(
    hospitalId: UUID,
    type: String,
    content: JsonNode,
  ): ConfigDocument {
    require(type in TYPES) { "UNKNOWN_CONFIG_TYPE: $type" }
    require(hospitalRepository.existsById(hospitalId)) { "HOSPITAL_NOT_FOUND" }
    val violations = schemaRegistry.validate("config/$type.schema.json", content)
    require(violations.isEmpty()) { "CONFIG_VALIDATION_FAILED: ${violations.joinToString("; ")}" }
    // shortcut: concurrent publishes of the same type race on (hospital_id, type, doc_version); the DB unique
    // constraint makes one fail loudly — retry the PUT, split out if concurrent config editing ever lands
    val nextVersion =
      (configDocumentRepository.findFirstByHospitalIdAndTypeOrderByDocumentVersionDesc(hospitalId, type)?.documentVersion ?: 0) + 1
    configDocumentRepository.findByHospitalIdAndTypeAndStatus(hospitalId, type, "active").forEach { it.status = "superseded" }
    val saved =
      configDocumentRepository.save(
        ConfigDocument(
          hospitalId = hospitalId,
          type = type,
          documentVersion = nextVersion,
          content = content,
        ),
      )
    domainEventPublisher.publish(
      DomainEvent(
        eventId = UUID.randomUUID(),
        eventType = "config.changed",
        occurredAt = Instant.now(),
        source = "hospital-config",
        entityType = "config_document",
        entityId = saved.id,
        hospitalId = hospitalId,
        payload = mapOf("type" to type, "version" to nextVersion),
      ),
    )
    return saved
  }

  @Transactional(readOnly = true)
  fun latest(
    hospitalId: UUID,
    type: String,
  ): ConfigDocument? {
    require(type in TYPES) { "UNKNOWN_CONFIG_TYPE: $type" }
    return configDocumentRepository.findFirstByHospitalIdAndTypeAndStatusOrderByDocumentVersionDesc(hospitalId, type, "active")
  }

  @Transactional(readOnly = true)
  fun versions(
    hospitalId: UUID,
    type: String,
  ): List<ConfigDocument> {
    require(type in TYPES) { "UNKNOWN_CONFIG_TYPE: $type" }
    return configDocumentRepository.findByHospitalIdAndTypeOrderByDocumentVersionDesc(hospitalId, type)
  }

  @Transactional(readOnly = true)
  fun atVersion(
    hospitalId: UUID,
    type: String,
    documentVersion: Int,
  ): ConfigDocument? {
    require(type in TYPES) { "UNKNOWN_CONFIG_TYPE: $type" }
    return configDocumentRepository.findFirstByHospitalIdAndTypeAndDocumentVersion(hospitalId, type, documentVersion)
  }
}
