package com.squamed.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Outbox dispatcher (chunk-00 item 8): drains PENDING rows into Kafka + the Redis pub/sub channel
 * `squamed.events` (consumed by RealtimeEventBridge). Retry = exponential backoff capped at 60s;
 * after max_attempts the row is FAILED (dead-letter, replayable via the admin API).
 */
@Component
class EventDispatcher(
  private val outboxRepository: EventOutboxRepository,
  private val kafkaTemplate: KafkaTemplate<String, String>,
  private val redis: StringRedisTemplate,
  private val objectMapper: ObjectMapper,
  private val cursorService: EventCursorService,
) {
  private val logger = LoggerFactory.getLogger(EventDispatcher::class.java)

  @Scheduled(fixedDelay = 500)
  fun dispatch() {
    // Requeue DISPATCHING rows stuck past the lease (crash between claim and terminal write).
    val staleCutoff = Instant.now().minus(Duration.ofMinutes(5))
    outboxRepository.findByStatusAndUpdatedAtBefore("DISPATCHING", staleCutoff).forEach { row ->
      row.status = "PENDING"
      outboxRepository.save(row)
    }
    val batch =
      outboxRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
        "PENDING",
        Instant.now(),
        PageRequest.of(0, 50),
      )
    for (row in batch) {
      send(row)
    }
  }

  private fun send(event: EventOutbox) {
    // Reassign after each save: repository.save() merges the detached entity and does not
    // propagate the new @Version to the original instance, so reusing it fails the next update.
    var row = event
    try {
      row.status = "DISPATCHING"
      row = outboxRepository.save(row)
      val json = objectMapper.writeValueAsString(row.toWire(objectMapper))
      kafkaTemplate.send("squamed.domain-events", row.eventType, json)
      EventCatalog.perTypeTopic(row.eventType)?.let { topic ->
        kafkaTemplate.send(topic, row.eventType, json)
      }
      redis.convertAndSend("squamed.events", json)
      row.status = "SENT"
      row.dispatchedAt = Instant.now()
      row = outboxRepository.save(row)
      cursorService.advance("dispatcher", row.eventId, row.eventType, row.occurredAt)
    } catch (e: Exception) {
      row.attempts += 1
      row.lastError = e.message?.take(2000)
      if (row.attempts >= row.maxAttempts) {
        row.status = "FAILED"
        logger.error("Event {} moved to DLQ after {} attempts", row.eventId, row.attempts, e)
      } else {
        // shortcut: no claim lease / SKIP LOCKED — single app instance in dev; add claim semantics before >1 replica.
        val backoffSeconds = minOf(60L, 1L shl row.attempts.coerceAtMost(6))
        row.status = "PENDING"
        row.nextAttemptAt = Instant.now().plusSeconds(backoffSeconds)
      }
      outboxRepository.save(row)
    }
  }
}
