package com.squamed.core.events

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class EventCursorService(
  private val repository: EventCursorRepository,
) {
  /** Idempotent position update for a named consumer (dispatcher, notification bridge). */
  fun advance(
    consumer: String,
    eventId: UUID,
    eventType: String,
    occurredAt: Instant,
  ) {
    val cursor = repository.findByConsumer(consumer) ?: EventCursor(consumer = consumer)
    cursor.lastEventId = eventId
    cursor.lastEventType = eventType
    cursor.lastOccurredAt = occurredAt
    repository.save(cursor)
  }

  fun list(): List<EventCursor> = repository.findAll()
}
