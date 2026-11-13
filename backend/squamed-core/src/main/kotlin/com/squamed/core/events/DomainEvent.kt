package com.squamed.core.events

import java.time.Instant
import java.util.UUID

data class DomainEvent(
  val eventId: UUID,
  val eventType: String,
  val occurredAt: Instant,
  val source: String,
  val entityType: String,
  val entityId: UUID?,
  val hospitalId: UUID? = null,
  val payload: Map<String, Any?> = emptyMap(),
  val recipientIds: List<UUID> = emptyList(),
)
