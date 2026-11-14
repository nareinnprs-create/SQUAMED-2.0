package com.squamed.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Admin operations over the event fabric: outbox inspection/replay, DLQ, integration error replay. */
@Service
class EventOpsService(
  private val outboxRepository: EventOutboxRepository,
  private val integrationErrorRepository: IntegrationErrorRepository,
  private val cursorRepository: EventCursorRepository,
  private val integrationErrorService: IntegrationErrorService,
  private val replayers: List<IntegrationReplayer>,
  private val objectMapper: ObjectMapper,
) {
  fun outbox(
    status: String?,
    limit: Int,
  ): List<Map<String, Any?>> {
    val page = PageRequest.of(0, limit.coerceIn(1, 200), Sort.by(Sort.Direction.DESC, "updatedAt"))
    val rows =
      if (status == null) outboxRepository.findAll(page).content else outboxRepository.findByStatusOrderByUpdatedAtDesc(status, page)
    return rows.map { row ->
      mapOf(
        "id" to row.id,
        "eventId" to row.eventId,
        "eventType" to row.eventType,
        "status" to row.status,
        "attempts" to row.attempts,
        "maxAttempts" to row.maxAttempts,
        "nextAttemptAt" to row.nextAttemptAt,
        "lastError" to row.lastError,
        "dispatchedAt" to row.dispatchedAt,
        "hospitalId" to row.hospitalId,
        "createdAt" to row.createdAt,
        // Server-built wire envelope (DomainEvent JSON) — validated against event-envelope-v1 in PIT.
        "envelope" to objectMapper.writeValueAsString(row.toWire(objectMapper)),
      )
    }
  }

  /** FAILED → PENDING with attempts reset; pass an eventId for a single row, null for all FAILED. */
  @Transactional
  fun replayOutbox(eventId: UUID?): Map<String, Any?> {
    val rows =
      if (eventId != null) {
        outboxRepository.findByEventId(eventId)?.let { listOf(it) } ?: emptyList()
      } else {
        outboxRepository.findByStatusOrderByUpdatedAtDesc("FAILED", PageRequest.of(0, 200))
      }
    var requeued = 0
    rows.filter { it.status == "FAILED" }.forEach { row ->
      row.status = "PENDING"
      row.attempts = 0
      row.nextAttemptAt = Instant.now()
      row.lastError = null
      outboxRepository.save(row)
      requeued += 1
    }
    return mapOf("requeued" to requeued)
  }

  fun integrationErrors(
    integration: String?,
    status: String?,
    limit: Int,
  ): List<Map<String, Any?>> =
    integrationErrorService
      .list(integration, status, limit)
      .map { row ->
        mapOf(
          "id" to row.id,
          "hospitalId" to row.hospitalId,
          "integration" to row.integration,
          "direction" to row.direction,
          "messageType" to row.messageType,
          "payload" to row.payload,
          "error" to row.error,
          "status" to row.status,
          "replayCount" to row.replayCount,
          "replayedAt" to row.replayedAt,
          "lastReplayError" to row.lastReplayError,
          "createdAt" to row.createdAt,
        )
      }

  @Transactional
  fun replayIntegrationError(id: UUID): Map<String, Any?> {
    val row =
      integrationErrorRepository.findById(id).orElseThrow { IllegalArgumentException("INTEGRATION_ERROR_NOT_FOUND") }
    val replayer =
      replayers.firstOrNull { it.integration == row.integration }
        ?: throw IllegalArgumentException("NO_REPLAYER_FOR_INTEGRATION")
    row.replayCount += 1
    row.replayedAt = Instant.now()
    var ok = false
    var failure: String? = null
    try {
      ok = replayer.replay(row.payload.orEmpty())
      if (!ok) failure = "replay returned a failure ack"
    } catch (e: Exception) {
      failure = e.message?.take(2000)
    }
    if (ok) row.status = "REPLAYED"
    row.lastReplayError = failure
    return mapOf("id" to row.id, "status" to row.status, "replayCount" to row.replayCount, "ok" to ok, "error" to failure)
  }

  fun cursors(): List<Map<String, Any?>> =
    cursorRepository.findAll().map { cursor ->
      mapOf(
        "id" to cursor.id,
        "consumer" to cursor.consumer,
        "lastEventId" to cursor.lastEventId,
        "lastEventType" to cursor.lastEventType,
        "lastOccurredAt" to cursor.lastOccurredAt,
        "updatedAt" to cursor.updatedAt,
      )
    }
}
