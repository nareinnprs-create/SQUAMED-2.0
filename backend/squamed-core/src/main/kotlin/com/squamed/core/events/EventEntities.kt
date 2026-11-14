package com.squamed.core.events

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/**
 * Stored wire event (V48 `events.event_outbox`). Written in the caller's transaction by
 * [DomainEventPublisher]; relayed to Kafka/Redis by EventDispatcher; FAILED rows are the DLQ.
 */
@Entity
@Table(name = "event_outbox", schema = "events")
class EventOutbox(
  @Column(name = "event_id", nullable = false)
  var eventId: UUID,
  @Column(name = "event_type", nullable = false, length = 150)
  var eventType: String,
  @Column(name = "envelope_version", nullable = false)
  var envelopeVersion: Int = 1,
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: Instant,
  @Column(name = "source", nullable = false, length = 100)
  var source: String,
  @Column(name = "entity_type", nullable = false, length = 100)
  var entityType: String,
  @Column(name = "entity_id")
  var entityId: UUID? = null,
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  // ConfigDocument pattern: jsonb column surfaced as a JsonNode.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
  var payload: JsonNode,
  // PatientMergeLog.snapshot pattern: jsonb column surfaced as a JSON string.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "recipient_ids", columnDefinition = "jsonb", nullable = false)
  var recipientIds: String = "[]",
  @Column(name = "actor_type", nullable = false, length = 20)
  var actorType: String = "system",
  @Column(name = "actor_id")
  var actorId: UUID? = null,
  @Column(name = "correlation_id", length = 100)
  var correlationId: String? = null,
  @Column(name = "causation_id")
  var causationId: UUID? = null,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "PENDING",
  @Column(name = "attempts", nullable = false)
  var attempts: Int = 0,
  @Column(name = "max_attempts", nullable = false)
  var maxAttempts: Int = 8,
  @Column(name = "next_attempt_at", nullable = false)
  var nextAttemptAt: Instant = Instant.now(),
  @Column(name = "last_error", columnDefinition = "text")
  var lastError: String? = null,
  @Column(name = "dispatched_at")
  var dispatchedAt: Instant? = null,
) : BaseEntity() {
  /** Rebuild the wire event through [DomainEvent] so the envelope shape has one source of truth. */
  fun toWire(objectMapper: ObjectMapper): DomainEvent =
    DomainEvent(
      eventId = eventId,
      eventType = eventType,
      occurredAt = occurredAt,
      source = source,
      entityType = entityType,
      entityId = entityId,
      hospitalId = hospitalId,
      payload = objectMapper.convertValue(payload, object : TypeReference<Map<String, Any?>>() {}),
      recipientIds = objectMapper.readValue(recipientIds, object : TypeReference<List<UUID>>() {}),
      version = envelopeVersion,
      actorType = actorType,
      actorId = actorId,
      correlationId = correlationId,
      causationId = causationId,
    )
}

/** Delivery position of a named consumer (dispatcher, notification bridge) — V48 `events.event_cursor`. */
@Entity
@Table(name = "event_cursor", schema = "events")
class EventCursor(
  @Column(name = "consumer", nullable = false, length = 100)
  var consumer: String,
  @Column(name = "last_event_id")
  var lastEventId: UUID? = null,
  @Column(name = "last_event_type", length = 150)
  var lastEventType: String? = null,
  @Column(name = "last_occurred_at")
  var lastOccurredAt: Instant? = null,
) : BaseEntity()

/** Failed integration payload awaiting manual replay — V48 `events.integration_error` (DLQ). */
@Entity
@Table(name = "integration_error", schema = "events")
class IntegrationError(
  @Column(name = "hospital_id")
  var hospitalId: UUID? = null,
  @Column(name = "integration", nullable = false, length = 50)
  var integration: String,
  @Column(name = "direction", nullable = false, length = 20)
  var direction: String = "INBOUND",
  @Column(name = "message_type", length = 50)
  var messageType: String? = null,
  @Column(name = "payload", columnDefinition = "text")
  var payload: String? = null,
  @Column(name = "error", nullable = false, columnDefinition = "text")
  var error: String,
  @Column(name = "status", nullable = false, length = 20)
  var status: String = "OPEN",
  /** Processing attempts including the original failure (record() starts at 1). */
  @Column(name = "replay_count", nullable = false)
  var replayCount: Int = 0,
  @Column(name = "replayed_at")
  var replayedAt: Instant? = null,
  @Column(name = "last_replay_error", columnDefinition = "text")
  var lastReplayError: String? = null,
) : BaseEntity()

interface EventOutboxRepository : JpaRepository<EventOutbox, UUID> {
  fun findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
    status: String,
    cutoff: Instant,
    pageable: Pageable,
  ): List<EventOutbox>

  fun findByStatusAndUpdatedAtBefore(
    status: String,
    cutoff: Instant,
  ): List<EventOutbox>

  fun findByStatusOrderByUpdatedAtDesc(
    status: String,
    pageable: Pageable,
  ): List<EventOutbox>

  fun findByEventId(eventId: UUID): EventOutbox?
}

interface EventCursorRepository : JpaRepository<EventCursor, UUID> {
  fun findByConsumer(consumer: String): EventCursor?
}

interface IntegrationErrorRepository : JpaRepository<IntegrationError, UUID> {
  fun findByStatusOrderByCreatedAtDesc(
    status: String,
    pageable: Pageable,
  ): List<IntegrationError>

  fun findByIntegrationOrderByCreatedAtDesc(
    integration: String,
    pageable: Pageable,
  ): List<IntegrationError>

  fun findByIntegrationAndStatusOrderByCreatedAtDesc(
    integration: String,
    status: String,
    pageable: Pageable,
  ): List<IntegrationError>
}
