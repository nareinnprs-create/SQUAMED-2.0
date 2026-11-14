package com.squamed.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.squamed.core.common.api.CorrelationIdFilter
import com.squamed.core.identity.security.SquamedPrincipal
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

/**
 * Transactional outbox publisher (chunk-00 item 8): `publish` writes events.event_outbox inside
 * the caller's transaction (atomic with the state change); EventDispatcher relays to Kafka/Redis.
 * Failures propagate so the business transaction rolls back instead of silently dropping events.
 */
@Service
class DomainEventPublisher(
  private val outboxRepository: EventOutboxRepository,
  private val objectMapper: ObjectMapper,
) {
  fun publish(event: DomainEvent) {
    val principal = SecurityContextHolder.getContext().authentication?.principal as? SquamedPrincipal
    val stamped =
      event.copy(
        actorType = if (principal != null) "user" else event.actorType,
        actorId = principal?.userId ?: event.actorId,
        correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID) ?: event.correlationId,
      )
    outboxRepository.save(
      EventOutbox(
        eventId = stamped.eventId,
        eventType = stamped.eventType,
        occurredAt = stamped.occurredAt,
        source = stamped.source,
        entityType = stamped.entityType,
        entityId = stamped.entityId,
        hospitalId = stamped.hospitalId,
        payload = objectMapper.valueToTree(stamped.payload),
        recipientIds = objectMapper.writeValueAsString(stamped.recipientIds),
        actorType = stamped.actorType,
        actorId = stamped.actorId,
        correlationId = stamped.correlationId,
        causationId = stamped.causationId,
      ),
    )
  }
}
