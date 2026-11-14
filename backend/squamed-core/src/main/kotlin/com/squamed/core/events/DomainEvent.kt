package com.squamed.core.events

import java.time.Instant
import java.util.UUID

/**
 * Domain event envelope v1. `version/actorType/actorId/correlationId/causationId` complete the
 * chunk-00 v1 field list: the publisher stamps actor (SecurityContext) and correlationId (MDC).
 */
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
  val version: Int = 1,
  val actorType: String = "system",
  val actorId: UUID? = null,
  val correlationId: String? = null,
  val causationId: UUID? = null,
)
